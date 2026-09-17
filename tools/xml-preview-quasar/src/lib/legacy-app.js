import {compile, stylesheet, baseStyle, render, updateProgress} from './engine.js';
import {createShowcase} from './showcase.js';

const $ = id => document.getElementById(id);
const controller = createShowcase();
let bundle, revision, sourceName, dirty = false, extraStyles = new Set(), lastScreen;
const drafts = new Map(), generic = {}, logs = [];
const frame = $('preview');
const stage = $('viewport-stage');
let pickMode = false;
let selectedElement = null;
let moveMode = false;
let hoveredElement = null;
const windowOffsets = new Map();
let searchMatches = [], searchIndex = -1;
try {
    const sizes=JSON.parse(localStorage.getItem('mui-preview-layout') || '{}');
    for(const key of ['--left','--right','--console-height']) if(Number.isFinite(sizes[key])) $('workspace').style.setProperty(key,`${sizes[key]}px`);
} catch { /* invalid saved layout */ }
function setViewport(width, height) {
    // The iframe border is outside Minecraft's viewport; add it back so the inner CSS pixels match GuiScreen.width/height.
    frame.style.width = `${width + 2}px`;
    frame.style.height = `${height + 2}px`;
    $('viewport-label').textContent=`${width} × ${height}`;
    fitViewport();
}
function fitViewport() {
    const width=Number($('width').value)+2, height=Number($('height').value)+2;
    const canvas=document.querySelector('.canvas');
    if(!canvas.clientWidth || !canvas.clientHeight)return;
    const available=Math.max(1,canvas.clientWidth-40);
    const vertical=Math.max(1,canvas.clientHeight-40);
    const scale=$('fit').checked ? Math.min(1,available/width,vertical/height) : 1;
    stage.style.width=`${width*scale}px`;
    stage.style.height=`${height*scale}px`;
    frame.style.transform=`scale(${scale})`;
    $('zoom').textContent=`${Math.round(scale*100)}%`;
}
setViewport(854,480);
new ResizeObserver(fitViewport).observe(document.querySelector('.workbench'));
const simulated = () => $('adapter').value === 'showcase';
function log(message) {
    logs.unshift(`${new Date().toLocaleTimeString()} ${message}`);
    $('events').textContent = logs.slice(0,40).join('\n');
    $('console').textContent = logs.slice(0,80).join('\n');
}
function showError(error) {
    $('error').hidden = !error;
    $('error').textContent = error ? `${error.message || error}\n已保留上一次成功的预览。` : '';
}
function sync() {
    if (simulated()) controller.sync(frame.contentDocument);
    $('state').textContent = JSON.stringify(simulated() ? controller.state : generic,null,2);
    updateProgress(frame.contentDocument,Number($('progress').value));
}
function change(key,value) {
    if (key === 'action') {
        if (!simulated() || !controller.action(value)) log(`未注册 action：${value}（游戏 Java 不会在浏览器执行）`);
        else log(`action ${value}`);
    } else {
        if (simulated()) controller.input(key,value);
        generic[key]=value;
        log(`${key} = ${JSON.stringify(value)}`);
    }
    sync();
}
function inspect(el) {
    if (moveMode) return true;
    if (!pickMode) return false;
    if (el) selectElement(el);
    return true;
}
function hoverElement(el) {
    if (!pickMode || hoveredElement === el) return;
    hoveredElement?.removeAttribute('data-dev-hover');
    hoveredElement = el;
    el?.setAttribute('data-dev-hover','true');
    const row = document.querySelector(`.tree-row[data-index="${el?.dataset.muiIndex}"]`);
    document.querySelector('.tree-row.hovered')?.classList.remove('hovered');
    row?.classList.add('hovered');
    highlight(el);
}
function highlight(el) {
    const doc=frame.contentDocument;
    const overlay=doc?.getElementById('dev-overlay');
    if (!overlay) return;
    if (!el) {overlay.hidden=true;return;}
    const rect=el.getBoundingClientRect();
    overlay.hidden=false;
    overlay.style.cssText=`position:fixed!important;left:${rect.left}px!important;top:${rect.top}px!important;width:${rect.width}px!important;height:${rect.height}px!important;display:block!important;z-index:2147483647!important;pointer-events:none!important;background:rgba(70,150,230,.28)!important;outline:2px solid #57a7f2!important;box-shadow:0 0 0 1px rgba(255,255,255,.85) inset!important;transform:none!important;opacity:1!important;visibility:visible!important;clip:auto!important;filter:none!important`;
    const tooltip=doc.getElementById('dev-tooltip');
    tooltip.textContent=`${el.dataset.muiTag}${el.id?'#'+el.id:''}  ${Math.round(rect.width)} × ${Math.round(rect.height)}  (${Math.round(rect.x)}, ${Math.round(rect.y)})`;
    tooltip.style.setProperty('left',`${Math.max(0,Math.min(rect.left,doc.documentElement.clientWidth-tooltip.offsetWidth))}px`,'important');
    tooltip.style.setProperty('top',`${rect.top>25?rect.top-23:rect.bottom+3}px`,'important');
}
function matchingRules(el) {
    const output = $('matched-rules'); output.replaceChildren();
    const doc = frame.contentDocument;
    const internal=document.createElement('details');internal.className='internal-rules';
    const summary=document.createElement('summary');summary.textContent='ModularUI default theme';internal.append(summary);
    const displayNames=style=>{
        const names=[...style], groups=['background','border','outline','padding','margin','font'];
        const collapsed=new Set(), result=[];
        const sides=['top','right','bottom','left'];
        const borderParts=['width','style','color'];
        const borderMembers=borderParts.flatMap(part=>sides.map(side=>`border-${side}-${part}`));
        if(borderMembers.every(name=>names.includes(name)) && borderParts.every(part=>
            sides.every(side=>style.getPropertyValue(`border-${side}-${part}`)===style.getPropertyValue(`border-top-${part}`)))){
            borderMembers.forEach(name=>collapsed.add(name));
            result.push({name:'border',value:borderParts.map(part=>style.getPropertyValue(`border-top-${part}`)).join(' '),members:borderMembers});
        }
        for(const group of groups) {
            const members=names.filter(name=>name.startsWith(group+'-')&&!collapsed.has(name));
            if(members.length>1 && style.getPropertyValue(group)) {
                result.push({name:group,value:style.getPropertyValue(group),members});members.forEach(name=>collapsed.add(name));
            }
        }
        return [...names.filter(name=>!collapsed.has(name)).map(name=>({name,value:style.getPropertyValue(name),members:[name]})),...result];
    };
    function walk(rules, originName) {
        for (const rule of rules) {
            if (rule.cssRules) { try { walk(rule.cssRules,originName); } catch { /* browser may reject inaccessible rules */ } }
            if (!rule.selectorText) continue;
            try { if (!el.matches(rule.selectorText)) continue; } catch { continue; }
            const item=document.createElement('div');item.className='rule';
            const selector=document.createElement('div');selector.className='selector';selector.textContent=rule.selectorText;
            const origin=document.createElement('div');origin.className='origin';origin.textContent=originName;
            const props=document.createElement('div');props.className='properties';
            for(const {name,value,members} of displayNames(rule.style)) {
                const priority=rule.style.getPropertyPriority(members[0]);
                const line=document.createElement('div');line.className='property';
                const enabled=document.createElement('input');enabled.type='checkbox';enabled.checked=true;enabled.title=`Toggle ${name}`;
                const key=document.createElement('span');key.className='property-name';key.textContent=name;
                const edit=document.createElement('input');edit.className='property-value';edit.value=value;edit.title=`Edit ${name}`;
                enabled.onchange=()=>{line.classList.toggle('disabled',!enabled.checked);members.forEach(member=>rule.style.removeProperty(member));if(enabled.checked)rule.style.setProperty(name,edit.value,priority);updateComputed(el);};
                edit.onchange=()=>{if(enabled.checked)rule.style.setProperty(name,edit.value,priority);updateComputed(el);};
                line.append(enabled,key,document.createTextNode(':'),edit,document.createTextNode(';'));props.append(line);
            }
            item.append(selector,origin,props);
            if(originName==='ModularUI defaults')internal.append(item);else output.prepend(item);
        }
    }
    for (const sheet of doc.styleSheets) { try {walk(sheet.cssRules,sheet.ownerNode?.dataset.source||'preview base');} catch { /* no external stylesheets are expected */ } }
    if(internal.children.length>1)output.append(internal);
    if (!output.children.length) output.textContent='No matching stylesheet rules.';
}
function selectElement(el) {
    if (!el || !el.dataset?.muiTag) return;
    if (selectedElement) selectedElement.removeAttribute('data-dev-selected');
    selectedElement = el; selectedElement.setAttribute('data-dev-selected','true');
    const node = el.muiNode || {};
    $('selected').textContent = `<${node.tag || el.dataset.muiTag}${el.id ? ` id="${el.id}"` : ''}${el.className ? ` class="${el.className}"` : ''}>\n${$('screen').value} · node ${el.dataset.muiIndex}`;
    $('class-edit').value = el.className || '';
    $('inline-edit').value = el.getAttribute('style') || '';
    updateComputed(el);
    matchingRules(el);
    document.querySelector('.tree-row.selected')?.classList.remove('selected');
    const row=document.querySelector(`.tree-row[data-index="${el.dataset.muiIndex}"]`);
    row?.classList.add('selected'); row?.scrollIntoView({block:'nearest'});
    const path=[];
    for(let parent=el;parent?.dataset?.muiTag;parent=parent.parentElement)path.unshift(parent);
    $('breadcrumbs').replaceChildren(...path.map(node=>{
        const button=document.createElement('button');button.type='button';
        button.textContent=node.dataset.muiTag+(node.id?`#${node.id}`:'');
        button.onclick=()=>selectElement(node);return button;
    }));
    highlight(el);
}
function searchNodes(direction=0) {
    const query=$('node-search').value.trim().toLowerCase();
    for(const row of document.querySelectorAll('.tree-row.match'))row.classList.remove('match');
    searchMatches=query?[...frame.contentDocument.querySelectorAll('[data-mui-tag]')].filter(el=>
        [el.dataset.muiTag,el.id,el.className,el.getAttribute('bind'),el.muiNode?.attrs?.bind,el.muiNode?.attrs?.text].some(value=>String(value||'').toLowerCase().includes(query))):[];
    if(!searchMatches.length){searchIndex=-1;$('search-count').textContent=query?'0 / 0':'';return;}
    searchIndex=direction ? (searchIndex+direction+searchMatches.length)%searchMatches.length : 0;
    for(const el of searchMatches){
        const row=document.querySelector(`.tree-row[data-index="${el.dataset.muiIndex}"]`);
        row?.classList.add('match');
        for(let parent=row?.parentElement;parent?.id!=='dom-tree';parent=parent?.parentElement)if(parent?.classList.contains('tree-children')){
            parent.hidden=false;
            const arrow=parent.previousElementSibling?.querySelector('.arrow');if(arrow)arrow.textContent='▾';
        }
    }
    $('search-count').textContent=`${searchIndex+1} / ${searchMatches.length}`;
    selectElement(searchMatches[searchIndex]);
}
function updateComputed(el) {
    const style = frame.contentWindow.getComputedStyle(el), interesting = ['position','left','top','width','height','display','font-size','line-height','color','background-color','visibility','overflow','pointer-events'];
    $('computed').textContent = interesting.map(key => `${key}: ${style.getPropertyValue(key)}`).join('\n');
    const rect = el.getBoundingClientRect(), parent = el.offsetParent?.getBoundingClientRect();
    const box=(cls,label,value)=>{const part=document.createElement('div');part.className=cls;part.textContent=`${label} ${value}`;return part;};
    const margin=box('box-outer','margin',`${style.marginTop} ${style.marginRight} ${style.marginBottom} ${style.marginLeft}`);
    const border=box('box-border','border',`${style.borderTopWidth} ${style.borderRightWidth} ${style.borderBottomWidth} ${style.borderLeftWidth}`);
    const padding=box('box-padding','padding',`${style.paddingTop} ${style.paddingRight} ${style.paddingBottom} ${style.paddingLeft}`);
    padding.append(box('box-content','content',`${Math.round(rect.width)} × ${Math.round(rect.height)} @ ${Math.round(rect.left-(parent?.left||0))}, ${Math.round(rect.top-(parent?.top||0))}`));border.append(padding);margin.append(border);$('boxmodel').replaceChildren(margin);
    highlight(el);
}
function buildDomTree() {
    const tree = $('dom-tree'); tree.replaceChildren();
    const root = frame.contentDocument?.querySelector('[data-mui-tag]');
    if (!root) return;
    let count=0;
    function add(parent, el, depth=0) {
        if (depth > 64) return;
        count++;
        const children = [...el.children].filter(child => child.dataset.muiTag);
        const row=document.createElement('div');row.className='tree-row';row.dataset.index=el.dataset.muiIndex;
        row.style.paddingLeft=`${depth*15+4}px`;row.setAttribute('role','treeitem');row.tabIndex=0;
        const arrow=document.createElement('span');arrow.className='arrow';arrow.textContent=children.length?'▾':'';
        const tag=document.createElement('span');tag.className='tag';tag.textContent=`<${el.dataset.muiTag}`;
        row.append(arrow,tag);
        if(el.id){const id=document.createElement('span');id.className='id';id.textContent=` #${el.id}`;row.append(id);}
        if(el.className){const cls=document.createElement('span');cls.className='class';cls.textContent=` .${String(el.className).trim().replace(/\s+/g,'.')}`;row.append(cls);}
        row.append(document.createTextNode('>'));
        row.onclick=()=>selectElement(el);
        let sub;
        row.onkeydown=e=>{
            if(['Enter',' '].includes(e.key)){e.preventDefault();selectElement(el);}
            else if(e.key==='ArrowLeft' && children.length){e.preventDefault();sub.hidden=true;arrow.textContent='▸';}
            else if(e.key==='ArrowRight' && children.length){e.preventDefault();sub.hidden=false;arrow.textContent='▾';}
            else if(e.key==='ArrowUp'||e.key==='ArrowDown'){
                e.preventDefault();
                const visible=[...tree.querySelectorAll('.tree-row')].filter(item=>item.getClientRects().length);
                const next=visible[visible.indexOf(row)+(e.key==='ArrowUp'?-1:1)];
                if(next){next.focus();next.click();}
            }
        };
        parent.append(row);
        if (children.length) {
            sub=document.createElement('div');sub.className='tree-children';parent.append(sub);
            arrow.onclick=e=>{e.stopPropagation();sub.hidden=!sub.hidden;arrow.textContent=sub.hidden?'▸':'▾';};
            for(const child of children)add(sub,child,depth+1);
        }
    }
    add(tree,root);
    $('element-count').textContent=`${count} nodes`;
    searchNodes();
}
function rebuild() {
    if (!bundle || !$('screen').value) return;
    try {
        const files = {...bundle.files,...Object.fromEntries(drafts)};
        const manifest = JSON.parse(files['mui-app.json']);
        const tree = compile(files,manifest,$('screen').value);
        const styles=[...new Set([...(manifest.stylesheets || []),...extraStyles])];
        const mappedStyles=styles.map(path=>({path,css:stylesheet(files,[path])}));
        const doc = frame.contentDocument;
        const root = render(tree,doc,{log,change,inspect,hover:hoverElement});
        const fullScreen = $('screen').value.endsWith('/showcase.xml');
        const panel = $('screen').value.endsWith('/chest-256.xml')
            ? {width:342,height:480,name:'256 格设备面板'}
            : {width:194,height:222,name:'设备面板'};
        if (fullScreen) {
            root.style.left = '0'; root.style.top = '0'; root.style.width = '100%'; root.style.height = '100%';
            root.style.transform = 'none';
            $('geometry').textContent = `ModularScreen: relativeToScreen().full()\n模式: 全屏\n视口: ${$('width').value} × ${$('height').value}\n坐标原点: 屏幕左上角`;
        } else {
            root.style.left = `${Math.floor((Number($('width').value)-panel.width)/2)}px`;
            root.style.top = `${Math.floor((Number($('height').value)-panel.height)/2)}px`;
            root.dataset.previewPanel = '';
            root.style.setProperty('--mui-panel-width',`${panel.width}px`);
            root.style.setProperty('--mui-panel-height',`${panel.height}px`);
            root.style.transform = 'none';
            $('geometry').textContent = `ModularPanel: center()\n模式: ${panel.name}\n面板: ${panel.width} × ${panel.height}\n位置: (${Math.floor((Number($('width').value)-panel.width)/2)}, ${Math.floor((Number($('height').value)-panel.height)/2)})\n坐标原点: 面板左上角`;
        }
        const saved=windowOffsets.get($('screen').value);
        if(saved){root.style.left=`${saved.x}px`;root.style.top=`${saved.y}px`;root.style.transform='none';}
        installWindowDrag(root,doc);
        root.addEventListener('pointerdown', () => {if (simulated() && !pickMode) {controller.capture();sync();}},true);
        const style = doc.createElement('style');style.textContent=baseStyle;style.dataset.source='ModularUI defaults';
        const sheets=mappedStyles.map(({path,css})=>{const sheet=doc.createElement('style');sheet.dataset.source=path;sheet.textContent=css;return sheet;});
        // Validate all resources before replacing the last working view.
        doc.head.replaceChildren(style,...sheets);
        const overlay=doc.createElement('div');overlay.id='dev-overlay';overlay.hidden=true;
        const tooltip=doc.createElement('div');tooltip.id='dev-tooltip';overlay.append(tooltip);
        doc.body.replaceChildren(root,overlay);
        doc.body.classList.toggle('inspect',pickMode);
        doc.body.classList.toggle('move-mode',moveMode);
        selectedElement = null;
        hoveredElement = null;
        $('breadcrumbs').replaceChildren();
        buildDomTree();
        sync();showError(null);
        $('draft').textContent = drafts.size ? `${drafts.size} 个浏览器草稿已应用；磁盘文件未修改。` : '磁盘热更新已启用 · 每 750 ms 检查 XML / CSS / JSON';
        log(`预览已更新：${$('screen').value}`);
    } catch (error) {showError(error);}
}
function installWindowDrag(root,doc) {
    let drag=null;
    root.addEventListener('pointerdown',e=>{
        if(!moveMode || pickMode || e.button!==0)return;
        e.preventDefault();e.stopImmediatePropagation();
        const box=root.getBoundingClientRect();
        drag={pointer:e.pointerId,startX:e.clientX,startY:e.clientY,x:box.left,y:box.top};
        root.setPointerCapture(e.pointerId);
        doc.body.classList.add('moving-window');
    },true);
    root.addEventListener('pointermove',e=>{
        if(!drag || e.pointerId!==drag.pointer)return;
        const x=Math.round(drag.x+e.clientX-drag.startX),y=Math.round(drag.y+e.clientY-drag.startY);
        root.style.left=`${x}px`;root.style.top=`${y}px`;root.style.transform='none';
        windowOffsets.set($('screen').value,{x,y});
        $('geometry').textContent=`Dragged window: (${x}, ${y})\nViewport: ${$('width').value} × ${$('height').value}`;
        if(selectedElement)highlight(selectedElement);
    });
    const stop=e=>{if(!drag || e.pointerId!==drag.pointer)return;drag=null;doc.body.classList.remove('moving-window');};
    root.addEventListener('pointerup',stop);root.addEventListener('pointercancel',stop);
}
function setOptions(select, values, selected) {
    select.replaceChildren(...values.map(value => {const option=document.createElement('option');option.value=value;option.textContent=value;return option;}));
    select.value=values.includes(selected) ? selected : values[0] || '';
}
function updateFileList() {
    const list=$('file-list');list.replaceChildren();
    for(const path of Object.keys(bundle.files)){
        const button=document.createElement('button');button.type='button';button.textContent=path;button.title=path;
        button.classList.toggle('active',path===$('source').value);
        button.onclick=()=>{stashEditor();$('source').value=path;loadEditor();};list.append(button);
    }
}
function loadEditor() {
    sourceName=$('source').value;
    $('editor').value=drafts.get(sourceName) ?? bundle.files[sourceName] ?? '';
    dirty=false;
    for(const button of $('file-list').children)button.classList.toggle('active',button.textContent===sourceName);
}
function stashEditor() {
    if (dirty && sourceName) drafts.set(sourceName,$('editor').value);
}
function styleOptions(manifest) {
    $('styles').replaceChildren();
    for (const path of Object.keys(bundle.files).filter(p => p.endsWith('.css') && !manifest.stylesheets?.includes(p))) {
        const label=document.createElement('label'), input=document.createElement('input');input.type='checkbox';input.checked=extraStyles.has(path);
        input.onchange=() => { if (input.checked) extraStyles.add(path); else extraStyles.delete(path); rebuild(); };
        label.append(input,document.createTextNode(path));$('styles').append(label);
    }
}
function screenChanged() {
    controller.reset();
    // These styles are supplied in Java by InventoryXmlScreen, not in the manifest.
    if (simulated() && bundle.files['styles/devices.css']) {
        if (/\/(chest(?:-256)?|furnace)\.xml$/.test($('screen').value)) extraStyles.add('styles/devices.css');
        else extraStyles.delete('styles/devices.css');
    }
    if (/\/chest-256\.xml$/.test($('screen').value)) extraStyles.add('styles/large-chest.css');
    else extraStyles.delete('styles/large-chest.css');
    lastScreen=$('screen').value;
    styleOptions(JSON.parse(bundle.files['mui-app.json']));rebuild();
}
async function poll() {
    try {
        const response=await fetch('/api/snapshot',{headers:revision ? {'If-None-Match':`"${revision}"`} : {}});
        if (response.status !== 304) {
            const next=await response.json();
            if (!response.ok) throw Error(next.error || '资源读取失败');
            const manifest=JSON.parse(next.files['mui-app.json']);
            const first=!bundle;
            bundle=next;revision=next.revision;
            setOptions($('screen'),Object.keys(bundle.files).filter(p => p.startsWith('screens/') && p.endsWith('.xml')),lastScreen || 'screens/showcase.xml');
            setOptions($('source'),Object.keys(bundle.files),sourceName || $('screen').value);
            updateFileList();
            if (!dirty) loadEditor();
            if (first && manifest.owner === 'neofontrender_mui_xml_showcase') $('adapter').value='showcase';
            if (first) screenChanged();else {styleOptions(manifest);rebuild();}
        }
        $('connection').textContent='● 本地连接 · 自动刷新';
    } catch (error) {$('connection').textContent='● 资源/连接异常';showError(error);}
    finally {setTimeout(poll,750);}
}
$('source').onchange=() => {stashEditor();loadEditor();};
$('node-search').oninput=()=>searchNodes();
$('node-search').onkeydown=e=>{if(e.key==='Enter'){e.preventDefault();searchNodes(e.shiftKey?-1:1);}else if(e.key==='Escape'){$('node-search').value='';searchNodes();}};
$('search-prev').onclick=()=>searchNodes(-1);
$('search-next').onclick=()=>searchNodes(1);
for(const tab of document.querySelectorAll('[data-workspace]'))tab.onclick=()=>{
    const sources=tab.dataset.workspace==='sources';
    $('workspace').classList.toggle('sources-active',sources);
    $('sources-workspace').hidden=!sources;
    for(const button of document.querySelectorAll('[data-workspace]'))button.classList.toggle('active',button===tab);
    if(!sources)fitViewport();
};
document.addEventListener('keydown',e=>{
    if(e.ctrlKey && e.shiftKey && e.key.toLowerCase()==='c'){e.preventDefault();$('pick').click();document.querySelector('[data-workspace="elements"]').click();}
    else if(e.ctrlKey && e.key==='`'){e.preventDefault();toggleConsole();}
    else if(e.ctrlKey && e.key.toLowerCase()==='f' && document.activeElement!==$('editor')){e.preventDefault();document.querySelector('[data-workspace="elements"]').click();$('node-search').focus();$('node-search').select();}
    else if(e.key==='Escape' && pickMode)$('pick').click();
});
$('editor').oninput=() => {dirty=true;$('draft').textContent='编辑中 · 点击“应用草稿”预览；不会写入磁盘';};
$('apply').onclick=() => {stashEditor();dirty=false;rebuild();};
$('discard').onclick=() => {drafts.delete(sourceName);loadEditor();rebuild();};
$('screen').onchange=() => {try {screenChanged();} catch(e) {showError(e);}};
$('adapter').onchange=screenChanged;
$('reset').onclick=() => {controller.reset();for(const key of Object.keys(generic)) delete generic[key];rebuild();};
$('pick').onclick=() => {pickMode=!pickMode;$('pick').classList.toggle('active',pickMode);$('pick').textContent=pickMode?'⨯ 停止选择':'⊙ 选择元素';frame.contentDocument?.body.classList.toggle('inspect',pickMode);};
$('move-window').onclick=()=>{moveMode=!moveMode;$('move-window').classList.toggle('active',moveMode);frame.contentDocument?.body.classList.toggle('move-mode',moveMode);};
$('reset-position').onclick=()=>{windowOffsets.delete($('screen').value);rebuild();};
$('fit').onchange=fitViewport;
$('apply-element').onclick=() => {if (!selectedElement) return;selectedElement.className=$('class-edit').value;selectedElement.setAttribute('style',$('inline-edit').value);selectElement(selectedElement);log(`浏览器覆盖已应用到 ${selectedElement.dataset.muiTag}`);};
for(const tab of document.querySelectorAll('[data-tab]'))tab.onclick=()=>{
    for(const button of document.querySelectorAll('[data-tab]'))button.classList.toggle('active',button===tab);
    for(const panel of document.querySelectorAll('[data-panel]'))panel.hidden=panel.dataset.panel!==tab.dataset.tab;
};
function toggleConsole(force){
    const drawer=$('console-drawer');drawer.hidden=force===undefined?!drawer.hidden:!force;
    $('console-toggle').setAttribute('aria-expanded',String(!drawer.hidden));
}
$('console-toggle').onclick=()=>toggleConsole();
$('close-console').onclick=()=>toggleConsole(false);
$('clear-console').onclick=()=>{logs.length=0;$('console').textContent='';$('events').textContent='';};
for(const grip of document.querySelectorAll('.splitter, #drawer-grip')){
    grip.addEventListener('pointerdown',e=>{
        if(e.button!==0)return;
        e.preventDefault();grip.setPointerCapture(e.pointerId);grip.classList.add('dragging');
        const bounds=$('workspace').getBoundingClientRect();
        const move=event=>{
            if(grip.id==='drawer-grip')$('workspace').style.setProperty('--console-height',`${Math.max(90,Math.min(bounds.height-100,bounds.bottom-event.clientY))}px`);
            else if(grip.dataset.split==='left')$('workspace').style.setProperty('--left',`${Math.max(150,Math.min(bounds.width-460,event.clientX-bounds.left))}px`);
            else $('workspace').style.setProperty('--right',`${Math.max(250,Math.min(bounds.width-400,bounds.right-event.clientX))}px`);
        };
        const stop=()=>{grip.classList.remove('dragging');grip.removeEventListener('pointermove',move);grip.removeEventListener('pointerup',stop);grip.removeEventListener('pointercancel',stop);
            const sizes={};for(const key of ['--left','--right','--console-height'])sizes[key]=parseFloat(getComputedStyle($('workspace')).getPropertyValue(key));
            localStorage.setItem('mui-preview-layout',JSON.stringify(sizes));
        };
        grip.addEventListener('pointermove',move);grip.addEventListener('pointerup',stop);grip.addEventListener('pointercancel',stop);
    });
}
$('progress').oninput=sync;
$('resize').onclick=() => {
    for (const [id,min,max] of [['width',240,2560],['height',180,1600]]) {
        const value=Math.max(min,Math.min(max,Number($(id).value)||min));$(id).value=value;
    }
    setViewport(Number($('width').value),Number($('height').value));
    rebuild();
};
poll();
