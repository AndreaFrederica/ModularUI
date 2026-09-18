// Deliberately separate from Minecraft: XML components -> safe browser elements.
export function parseXML(source, name) {
    if (source.length > 2000000 || /<!DOCTYPE|<!ENTITY/i.test(source)) throw Error(`${name}: XML 太大或包含禁止的 DTD/entity`);
    const doc = new DOMParser().parseFromString(source, 'application/xml');
    const error = doc.querySelector('parsererror');
    if (error) throw Error(`${name}: ${error.textContent}`);
    let count = 0;
    function read(el, depth = 0) {
        if (++count > 10000 || depth > 64) throw Error(`${name}: XML 超出节点/深度限制`);
        if (el.nodeType === 3 || el.nodeType === 4) return el.textContent.trim() ? el.textContent : null;
        if (el.nodeType !== 1) return null;
        return {tag: el.tagName, attrs: Object.fromEntries([...el.attributes].filter(a => !a.name.startsWith('xmlns')).map(a => [a.name, a.value])), children: [...el.childNodes].map(n => read(n, depth + 1)).filter(n => n !== null)};
    }
    return read(doc.documentElement);
}

export function resource(files, path) {
    if (!Object.hasOwn(files, path)) throw Error(`资源不存在：${path}`);
    return files[path];
}

export function compile(files, manifest, screen) {
    if (manifest.formatVersion !== 1) throw Error('只支持 manifest formatVersion: 1');
    const templates = new Map();
    const interpolate = (text, props) => text.replace(/\$\{([^}]+)\}/g, (match, key) => props[key] ?? match);
    function clone(node, props, invocation) {
        if (typeof node === 'string') return interpolate(node, props);
        const copy = {tag: node.tag, attrs: Object.fromEntries(Object.entries(node.attrs).map(([k,v]) => [k, interpolate(v, props)])), children: []};
        for (const child of node.children) {
            if (child.tag === 'mui:slot') {
                const matches = invocation.children.filter(n => (typeof n === 'string' ? undefined : n.attrs.slot) === child.attrs.name);
                copy.children.push(...(matches.length ? matches : child.children).map(n => clone(n, matches.length ? props : {}, invocation)));
            } else copy.children.push(clone(child, props, invocation));
        }
        if (copy.tag === 'mui:component-root') return copy.children.find(n => typeof n !== 'string') || copy;
        return copy;
    }
    let expanded = 0;
    function expand(node, stack = []) {
        if (++expanded > 20000 || stack.length > 32) throw Error('组件展开超出限制');
        if (typeof node === 'string') return node;
        const name = node.tag === 'mui:component' ? node.attrs.component : node.tag;
        const path = manifest.components?.[name];
        if (path) {
            if (stack.includes(name)) throw Error(`递归组件：${[...stack, name].join(' → ')}`);
            if (!templates.has(path)) templates.set(path, parseXML(resource(files, path), path));
            const template = structuredClone(templates.get(path));
            // Match MuiComponentCompiler: expand nested template components before props/slots.
            template.children = template.children.map(n => expand(n, [...stack, name]));
            const result = clone(template, node.attrs, node);
            for (const [key,value] of Object.entries(node.attrs)) {
                if (!['component','name','slot'].includes(key) && !Object.hasOwn(result.attrs, key)) result.attrs[key] = value;
            }
            return expand(result, [...stack, name]);
        }
        return {...node, children: node.children.map(n => expand(n, stack))};
    }
    return expand(parseXML(resource(files, screen), screen));
}

export function stylesheet(files, paths) {
    function normalizePath(path) {
        const parts = [];
        for (const part of path.replaceAll('\\', '/').split('/')) {
            if (!part || part === '.') continue;
            if (part === '..') parts.pop();
            else parts.push(part);
        }
        return parts.join('/');
    }
    function load(path, stack = []) {
        path = normalizePath(path);
        if (stack.includes(path) || stack.length > 32) throw Error(`CSS @import 循环：${path}`);
        let css = resource(files, path).replace(/\/\*[\s\S]*?\*\//g, '');
        css = css.replace(/@import\s+(?:url\(\s*)?["']([^"']+)["']\s*\)?\s*;/g, (_, imported) => {
            const base = path.includes('/') ? path.slice(0, path.lastIndexOf('/') + 1) : '';
            const relative = normalizePath(`${base}${imported}`);
            const resolved = Object.hasOwn(files, relative) ? relative : normalizePath(imported);
            return load(resolved, [...stack, path]);
        });
        if (/@import|url\s*\(/i.test(css)) throw Error(`${path}: 预览不加载外部 CSS/图片 URL`);
        return css;
    }
    return paths.map(path => load(path)).join('\n')
        .replace(/\b([\w-]+)\|([\w-]+)/g, '[data-mui-tag="$1:$2"]')
        .replace(/:checked\b/g, '[checked="true"]')
        .replace(/font-size\s*:\s*(\d*\.?\d+)\s*;/g, (_,n) => `font-size:${Number(n)*12}px;`)
        .replace(/((?:max-|min-)?(?:width|height)|left|right|top|bottom)\s*:\s*(\d*\.?\d+)\s*;/g, '$1:$2px;')
        .replace(/text-align\s*:\s*center-left\s*;/g, 'text-align:left;justify-content:flex-start;')
        .replace(/text-align\s*:\s*center\s*;/g, 'text-align:center;justify-content:center;')
        .replace(/(?:grid-columns|columns)\s*:\s*(\d+)\s*;/g, 'grid-template-columns:repeat($1, max-content);')
        .replace(/progress-(track-color|fill-color|direction)\s*:/g, '--progress-$1:')
        .replace(/slider-(track-color|track-height|track-inset|handle-color|handle-width|handle-height)\s*:/g, '--mui-slider-$1:');
}

export const baseStyle = `@layer mui-default {
*{box-sizing:border-box}html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#11161d;color:#404040;font:12px Arial,sans-serif}
[data-mui-tag]{position:absolute;min-width:0;min-height:0;flex-shrink:0;pointer-events:none}
:where([data-preview-panel]){width:var(--mui-panel-width);height:var(--mui-panel-height)}
[data-mui-tag="mui:container"]{color:#404040}
:where([data-mui-tag="mui:row"],[data-mui-tag="mui:column"],[data-mui-tag="mui:list"]){display:flex;gap:0}
:where([data-mui-tag="mui:column"],[data-mui-tag="mui:list"]){flex-direction:column}
:where([data-mui-tag="mui:grid"]){display:grid;grid-template-columns:var(--mui-grid-columns,repeat(1,max-content));align-content:start}
[data-flow-child]{position:relative}
[data-mui-tag="mui:text"]{display:flex;align-items:center;white-space:pre-wrap}
[data-mui-tag="mui:scroll"]{overflow:auto;pointer-events:auto}
button:where([data-mui-tag]){-webkit-appearance:none;appearance:none;width:18px;height:18px;border:0;border-radius:0;padding:0;color:#fff;background:linear-gradient(#c6c6c6 0 1px,transparent 1px calc(100% - 1px),#3f3f3f calc(100% - 1px)),linear-gradient(90deg,#c6c6c6 0 1px,#858585 1px calc(100% - 1px),#3f3f3f calc(100% - 1px));box-shadow:none;text-shadow:1px 1px #3f3f3f;font:inherit;text-align:center;cursor:pointer;pointer-events:auto}
button:where([data-mui-tag]):hover{filter:brightness(1.12)}button:where([data-mui-tag]):active{filter:brightness(.84);text-shadow:none}
:where(button[data-mui-tag][disabled]){color:#8b8b8b;filter:brightness(.72);text-shadow:none;cursor:default}
:where(button[data-mui-tag][checked="true"]){background:#4b7866}
input:where([data-mui-tag]){-webkit-appearance:none;appearance:none;margin:0;width:56px;height:18px;border:0;border-radius:0;padding:2px 3px;color:#fff;background:linear-gradient(#111 0 1px,transparent 1px calc(100% - 1px),#777 calc(100% - 1px)),linear-gradient(90deg,#111 0 1px,#202020 1px calc(100% - 1px),#777 calc(100% - 1px));box-shadow:none;font:inherit;pointer-events:auto}
[data-mui-css-background]{box-shadow:none;border-radius:0}
button[data-mui-css-background]:hover{filter:none}button[data-mui-css-background]:active{filter:none}
button[data-mui-tag]:focus-visible,input:focus-visible{outline:2px solid #70d9c8;outline-offset:-2px}
 :where([data-mui-tag="mui:item-slot"]){width:18px;height:18px;background:#8b8b8b;border:1px solid #373737;box-shadow:inset 1px 1px #c6c6c6,inset -1px -1px #5b5b5b;pointer-events:auto;cursor:pointer;color:#fff;font:10px monospace;display:flex;align-items:center;justify-content:center}
:where([data-mui-tag="mui:item-slot"]):hover{background:#a0a0a0;box-shadow:inset 0 0 0 1px #fff}
:where([data-mui-tag="nfr:slider"]){width:auto;min-width:56px;height:18px;background:transparent;border:0;box-shadow:none;padding:0;cursor:pointer;pointer-events:auto}
:where([data-mui-tag="nfr:slider"])>[data-slider-track]{position:absolute;left:var(--mui-slider-track-inset,0px);right:var(--mui-slider-track-inset,0px);top:calc(50% - var(--mui-slider-track-height,0px)/2);height:var(--mui-slider-track-height,0px);background:var(--mui-slider-track-color,transparent);pointer-events:none}
:where([data-mui-tag="nfr:slider"])>[data-slider-thumb]{position:absolute;left:calc((100% - var(--mui-slider-handle-width,6px))*var(--mui-slider-value, .5));top:calc(50% - var(--mui-slider-handle-height,100%)/2);width:var(--mui-slider-handle-width,6px);height:var(--mui-slider-handle-height,100%);background:var(--mui-slider-handle-color,transparent);pointer-events:none}
:where([data-mui-tag="nfr:slider"])>[data-slider-input]{position:absolute;inset:0;z-index:1;width:100%;height:100%;margin:0;padding:0;border:0;opacity:0;cursor:pointer;pointer-events:auto}
:where([data-mui-tag="nfr:slider"]):focus-within{outline:2px solid #70d9c8;outline-offset:-2px}
:where([data-mui-tag="nfr:text-field"]){width:56px;height:18px}
:where([data-mui-tag="mui:progress"]){width:100%;height:100%;background:var(--progress-track-color,#27323d)}
[data-mui-tag="mui:progress"]>i{position:absolute;left:0;bottom:0;background:var(--progress-fill-color,#38bda6)}
[data-unknown]{outline:1px dashed #e3b45b}
body.inspect [data-mui-tag]{pointer-events:auto!important}body.inspect [data-mui-tag]:hover{outline:1px solid #ffcc66}
[data-dev-hover]{outline:2px solid #ffcc66!important;outline-offset:1px}
[data-dev-selected]{outline:2px solid #7ce0c7!important;outline-offset:1px}
body.move-mode [data-mui-tag]{pointer-events:auto!important;cursor:grab!important}body.moving-window [data-mui-tag]{cursor:grabbing!important}
#dev-overlay{position:fixed;z-index:2147483647;pointer-events:none;background:#4696e647;outline:2px solid #57a7f2;box-sizing:border-box}
#dev-overlay[hidden]{display:none}#dev-tooltip{position:fixed;z-index:100000;width:max-content;max-width:95vw;padding:2px 5px;background:#e8f1fc;color:#172b41;font:11px monospace;white-space:nowrap;box-shadow:0 1px 3px #0008}
}
`;

export function render(tree, doc, {log, change, inspect, hover}) {
    const known = new Set(['mui:container','mui:row','mui:column','mui:grid','mui:scroll','mui:list','mui:button','mui:text','mui:item-slot','mui:progress','nfr:slider','nfr:text-field']);
    let elementIndex = 0;
    function make(node, parentTag) {
        if (typeof node === 'string') return doc.createTextNode(node);
        const tag = node.tag;
        const el = doc.createElement(tag === 'mui:button' ? 'button' : tag === 'nfr:text-field' ? 'input' : 'div');
        el.dataset.muiTag = tag;
        el.dataset.muiIndex = String(elementIndex++);
        el.muiNode = node;
        if (node.attrs.onclick) el.dataset.muiAction = node.attrs.onclick;
        // Never copy arbitrary HTML attributes such as onclick, src, or style.
        for (const [key,value] of Object.entries(node.attrs)) {
            if (['id','class','checked','bind'].includes(key) || key.startsWith('data-')) el.setAttribute(key,value);
        }
        if (['mui:row','mui:column','mui:grid','mui:list'].includes(parentTag)) el.dataset.flowChild = '';
        if (!known.has(tag)) { el.dataset.unknown = ''; log(`未适配原生元素 ${tag}，显示为容器`); }
        if (tag === 'mui:text') el.textContent = node.attrs.text ?? '';
        if (tag === 'mui:grid' && node.attrs.columns) el.style.setProperty('--mui-grid-columns', `repeat(${Math.max(1, Math.min(128, Number(node.attrs.columns) || 1))},max-content)`);
        if (tag === 'mui:button') {
            el.type = 'button';
            el.style.pointerEvents = 'auto';
            el.disabled = node.attrs.disabled === 'true';
            el.addEventListener('click', () => {
                const action = node.attrs.onclick || el.dataset.muiAction;
                if (action) change('action', action, el);
                else if (el.hasAttribute('checked')) { el.setAttribute('checked', String(el.getAttribute('checked') !== 'true')); change('checked',el.getAttribute('checked'),el); }
                else log(`click ${node.attrs.id || tag}`);
            });
        }
        if (tag === 'nfr:slider') {
            el.style.pointerEvents = 'auto';
            const track = doc.createElement('i');
            const thumb = doc.createElement('i');
            const input = doc.createElement('input');
            track.dataset.sliderTrack = '';
            thumb.dataset.sliderThumb = '';
            input.dataset.sliderInput = '';
            input.type = 'range';
            for (const key of ['min','max','step']) if (node.attrs[key]) el.setAttribute(key,node.attrs[key]);
            for (const key of ['min','max','step']) if (node.attrs[key]) input.setAttribute(key,node.attrs[key]);
            input.value = node.attrs.value ?? '1';
            input.setAttribute('aria-label', node.attrs.id || 'slider');
            const updateSlider = () => {
                const min = Number(input.min || 0), max = Number(input.max || 100);
                el.style.setProperty('--mui-slider-value', String(max === min ? 0 : (Number(input.value) - min) / (max - min)));
            };
            updateSlider();
            input.addEventListener('input', () => {
                updateSlider();
                change(node.attrs['store-key'] || node.attrs.id || 'value', Number(input.value), el);
            });
            el.append(track, thumb, input);
        }
        if (tag === 'nfr:text-field') {
            el.style.pointerEvents = 'auto';
            el.type = 'text';
            if (node.attrs['max-length']) el.maxLength = Number(node.attrs['max-length']);
            el.value = node.attrs.value ?? '';
            el.addEventListener('input', () => change(node.attrs['store-key'] || node.attrs.id || 'value',el.value,el));
        }
        if (tag === 'mui:item-slot') {
            el.style.pointerEvents = 'auto';
            el.tabIndex = 0; el.setAttribute('role','button'); el.setAttribute('aria-label',node.attrs.bind || '模拟物品槽');
            el.title = `${node.attrs.bind || 'slot'} · 点击模拟放入/取出物品`;
            const toggle = () => { el.textContent = el.textContent ? '' : '◆'; change('slot', {bind:node.attrs.bind, occupied:!!el.textContent},el); };
            el.addEventListener('click',toggle);
            el.addEventListener('keydown', e => { if (['Enter',' '].includes(e.key)) {e.preventDefault();toggle();} });
        }
        if (tag === 'mui:progress') el.append(doc.createElement('i'));
        for (const child of node.children) el.append(make(child,tag));
        return el;
    }
    const root = make(tree);
    root.style.pointerEvents = 'auto';
    const pickTarget = e => {
        const candidates=doc.elementsFromPoint(e.clientX,e.clientY)
            .filter(el=>el.dataset?.muiTag && el.getBoundingClientRect().width && el.getBoundingClientRect().height);
        return candidates.sort((a,b)=>{
            const ra=a.getBoundingClientRect(), rb=b.getBoundingClientRect();
            const area=ra.width*ra.height-rb.width*rb.height;
            if(area)return area;
            if(a.contains(b))return 1;
            if(b.contains(a))return -1;
            return 0;
        })[0] || e.target.closest('[data-mui-tag]');
    };
    root.addEventListener('pointerover', e => hover(pickTarget(e)), true);
    // Capture blocks interactions while inspecting, without changing simulated state.
    root.addEventListener('click', e => { if (inspect(pickTarget(e))) {e.preventDefault();e.stopImmediatePropagation();} },true);
    return root;
}

export function updateProgress(doc, value) {
    for (const el of doc.querySelectorAll('[data-mui-tag="mui:progress"]')) {
        const vertical = ['up','down'].includes(doc.defaultView.getComputedStyle(el).getPropertyValue('--progress-direction').trim());
        el.firstChild.style.width = vertical ? '100%' : `${value}%`;
        el.firstChild.style.height = vertical ? `${value}%` : '100%';
    }
}
