// Opt-in browser counterpart of the example mod's ShowcaseController.
export function createShowcase() {
    const initial = () => ({page:'overview',fontEnabled:true,shadowEnabled:false,mode:'Balanced',scale:1,sampleText:'The quick brown fox',events:0,runtimeRows:0,status:'Preview ready (simulated)'});
    let state = initial();
    return {
        get state() { return state; },
        reset() { state = initial(); },
        input(key,value) { state[key] = value; },
        capture() { state.events++; },
        action(name) {
            const actions = {
                'showcase:nav-overview': () => state.page = 'overview',
                'showcase:nav-components': () => state.page = 'components',
                'showcase:toggle-font': () => state.fontEnabled = !state.fontEnabled,
                'showcase:toggle-shadow': () => state.shadowEnabled = !state.shadowEnabled,
                'showcase:cycle-mode': () => {const modes=['Balanced','Quality','Performance'];state.mode=modes[(modes.indexOf(state.mode)+1)%modes.length];},
                'showcase:add-row': () => state.runtimeRows++,
                'showcase:remove-row': () => state.runtimeRows = Math.max(0,state.runtimeRows-1),
                'showcase:reset': () => state = initial(),
                'device:close': () => state.status = '模拟关闭请求（保留预览画布）'
            };
            if (!Object.hasOwn(actions,name)) return false;
            actions[name]();
            if (name !== 'device:close') state.status = name;
            return true;
        },
        sync(doc) {
            const get = id => doc.getElementById(id);
            const texts = {'status-text':'status','runtime-count':'runtimeRows','event-count':'events','scale-value':'scale','sample-preview':'sampleText','mode-value':'mode'};
            for (const [id,key] of Object.entries(texts)) if (get(id)) get(id).textContent=String(state[key]);
            for (const page of ['overview','components']) {
                get(`page-${page}`)?.setAttribute('data-active',String(state.page === page));
                get(`nav-${page}`)?.setAttribute('data-selected',String(state.page === page));
            }
            get('toggle-font')?.setAttribute('checked',String(state.fontEnabled));
            get('toggle-shadow')?.setAttribute('checked',String(state.shadowEnabled));
            for (const [id,key] of [['sample-input','sampleText'],['scale-slider','scale']]) {
                const el = get(id);
                if (el && el.value !== String(state[key])) el.value=String(state[key]);
            }
            const list = get('runtime-list');
            if (list && list.childElementCount !== state.runtimeRows) {
                list.replaceChildren();
                for (let i=1;i<=state.runtimeRows;i++) {
                    const row=doc.createElement('div'); row.className='runtime-row';row.dataset.muiTag='mui:container';row.dataset.flowChild='';
                    for (const [cls,text] of [['runtime-name',`Runtime component ${i}`],['runtime-badge','DOM']]) {
                        const label=doc.createElement('div');label.dataset.muiTag='mui:text';label.className=cls;label.textContent=text;row.append(label);
                    }
                    list.append(row);
                }
            }
        }
    };
}
