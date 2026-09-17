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
    function load(path, stack = []) {
        if (stack.includes(path) || stack.length > 32) throw Error(`CSS @import 循环：${path}`);
        let css = resource(files, path).replace(/\/\*[\s\S]*?\*\//g, '');
        css = css.replace(/@import\s+(?:url\(\s*)?["']([^"']+)["']\s*\)?\s*;/g, (_, imported) => load(imported, [...stack, path]));
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
        .replace(/progress-(track-color|fill-color|direction)\s*:/g, '--progress-$1:');
}

export const baseStyle = `
*{box-sizing:border-box}html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#11161d;color:#404040;font:12px Arial,sans-serif}
[data-mui-tag]{position:absolute;min-width:0;min-height:0;flex-shrink:0;pointer-events:none}
:where([data-preview-panel]){width:var(--mui-panel-width);height:var(--mui-panel-height)}
[data-mui-tag="mui:container"]{color:#404040}
[data-mui-tag="mui:row"],[data-mui-tag="mui:column"],[data-mui-tag="mui:list"]{display:flex;gap:0}
[data-mui-tag="mui:column"],[data-mui-tag="mui:list"]{flex-direction:column}
[data-mui-tag="mui:grid"]{display:grid;grid-template-columns:repeat(1,max-content);align-content:start}
[data-flow-child]{position:relative}
[data-mui-tag="mui:text"]{display:flex;align-items:center;white-space:pre-wrap}
[data-mui-tag="mui:scroll"]{overflow:auto;pointer-events:auto}
button:where([data-mui-tag]){width:18px;height:18px;border:1px solid #555;padding:0 2px;color:#fff;background:linear-gradient(#8b8b8b 0 50%,#707070 50%);box-shadow:inset 1px 1px #c6c6c6,inset -1px -1px #3f3f3f;text-shadow:1px 1px #3f3f3f;font:inherit;text-align:center;cursor:pointer;pointer-events:auto}
button:where([data-mui-tag]):hover{background:linear-gradient(#a0a0a0 0 50%,#858585 50%)}button:where([data-mui-tag]):active{background:linear-gradient(#707070 0 50%,#8b8b8b 50%);text-shadow:none}
button:where([data-mui-tag])[disabled]{color:#8b8b8b;background:linear-gradient(#707070 0 50%,#606060 50%);text-shadow:none;cursor:default}
button:where([data-mui-tag])[checked="true"]{background:linear-gradient(#5b8d78 0 50%,#3e6657 50%)}
input:where([data-mui-tag]){margin:0;width:56px;height:18px;border:1px solid #555;padding:2px 3px;color:#fff;background:#202020;box-shadow:inset 1px 1px #111,inset -1px -1px #777;font:inherit;pointer-events:auto;accent-color:#2f72a8}
button[data-mui-tag]:focus-visible,input:focus-visible{outline:2px solid #70d9c8;outline-offset:-2px}
 :where([data-mui-tag="mui:item-slot"]){width:18px;height:18px;background:#8b8b8b;border:1px solid #373737;box-shadow:inset 1px 1px #c6c6c6,inset -1px -1px #5b5b5b;pointer-events:auto;cursor:pointer;color:#fff;font:10px monospace;display:flex;align-items:center;justify-content:center}
:where([data-mui-tag="mui:item-slot"]):hover{background:#a0a0a0;box-shadow:inset 0 0 0 1px #fff}
:where([data-mui-tag="nfr:slider"]){width:56px;height:18px;background:transparent;border:0;box-shadow:none;padding:0}
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
`;

export function render(tree, doc, {log, change, inspect, hover}) {
    const known = new Set(['mui:container','mui:row','mui:column','mui:grid','mui:scroll','mui:list','mui:button','mui:text','mui:item-slot','mui:progress','nfr:slider','nfr:text-field']);
    let elementIndex = 0;
    function make(node, parentTag) {
        if (typeof node === 'string') return doc.createTextNode(node);
        const tag = node.tag;
        const el = doc.createElement(tag === 'mui:button' ? 'button' : tag === 'nfr:slider' || tag === 'nfr:text-field' ? 'input' : 'div');
        el.dataset.muiTag = tag;
        el.dataset.muiIndex = String(elementIndex++);
        el.muiNode = node;
        // Never copy arbitrary HTML attributes such as onclick, src, or style.
        for (const [key,value] of Object.entries(node.attrs)) {
            if (['id','class','checked','bind'].includes(key) || key.startsWith('data-')) el.setAttribute(key,value);
        }
        if (['mui:row','mui:column','mui:grid','mui:list'].includes(parentTag)) el.dataset.flowChild = '';
        if (!known.has(tag)) { el.dataset.unknown = ''; log(`未适配原生元素 ${tag}，显示为容器`); }
        if (tag === 'mui:text') el.textContent = node.attrs.text ?? '';
        if (tag === 'mui:grid' && node.attrs.columns) el.style.gridTemplateColumns = `repeat(${Math.max(1, Math.min(128, Number(node.attrs.columns) || 1))},max-content)`;
        if (tag === 'mui:button') {
            el.type = 'button';
            el.disabled = node.attrs.disabled === 'true';
            el.addEventListener('click', () => {
                if (node.attrs.onclick) change('action', node.attrs.onclick, el);
                else if (el.hasAttribute('checked')) { el.setAttribute('checked', String(el.getAttribute('checked') !== 'true')); change('checked',el.getAttribute('checked'),el); }
                else log(`click ${node.attrs.id || tag}`);
            });
        }
        if (el.tagName === 'INPUT') {
            el.type = tag === 'nfr:slider' ? 'range' : 'text';
            for (const key of ['min','max','step']) if (node.attrs[key]) el.setAttribute(key,node.attrs[key]);
            if (node.attrs['max-length']) el.maxLength = Number(node.attrs['max-length']);
            el.value = node.attrs.value ?? (el.type === 'range' ? '1' : '');
            el.addEventListener('input', () => change(node.attrs['store-key'] || node.attrs.id || 'value',el.type === 'range' ? Number(el.value) : el.value,el));
        }
        if (tag === 'mui:item-slot') {
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
