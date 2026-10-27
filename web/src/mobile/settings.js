/* Existing Core settings and select controllers; mobile presentation only. */
const agySettingsContext=A.createContext(false);
var G_=function AgySettingsPage(props){
  const mobile=useAgyMobileLayout();
  return A.createElement(agySettingsContext.Provider,{value:mobile},
    A.createElement(agyOriginalSettingsPage,props));
};
function AgySettingsSelectHeader({onClose}){
  return A.createElement("header",{className:"agy-settings-select-header"},
    A.createElement("strong",null,"Select an option"),
    A.createElement("button",{type:"button","aria-label":"Close list",onClick:onClose},"✕"));
}

function agySettingsListKeyDown(event){
  if(!["ArrowDown","ArrowUp","Home","End"].includes(event.key))return;
  const options=[...event.currentTarget.querySelectorAll('[role=option]:not(:disabled)')];
  if(!options.length)return;
  let index=options.indexOf(event.target.closest('[role=option]'));
  if(event.key==="Home")index=0;
  else if(event.key==="End")index=options.length-1;
  else index=(index+(event.key==="ArrowDown"?1:-1)+options.length)%options.length;
  event.preventDefault();options[index].focus({preventScroll:false});
}
