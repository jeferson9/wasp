const BeeRuntime = {

async init(){

return new Promise((resolve)=>{
setTimeout(resolve,1000);
});

},

async loadKeys(){

console.log("Loading mining keys");

return new Promise((resolve)=>{
setTimeout(resolve,1000);
});

},

async connectWallet(){

console.log("Request wallet authorization");

// simulação de autorização

return new Promise((resolve)=>{

setTimeout(()=>{

const approved = confirm(
"Authorize Bee Engine to run using your wallet?"
);

resolve(approved);

},500);

});

},

async start(){

console.log("Starting Bee node");

return new Promise((resolve)=>{
setTimeout(resolve,1500);
});

}

};
