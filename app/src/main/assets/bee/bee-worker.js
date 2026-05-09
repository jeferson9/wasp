function startBee(){

const status = document.getElementById("status");

status.innerText = "Bee Engine loading...";

setTimeout(()=>{
status.innerText = "Connecting to network...";
},1000);

setTimeout(()=>{
status.innerText = "Bee Engine running 🐝";
},2500);

}
