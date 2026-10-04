// 酷我版权片段不得触发任何播放包。
const mc = require('minecraft-protocol');
const fs = require('node:fs');
const result={playPackets:0,restrictionMessage:false,errors:[]};
const client=mc.createClient({host:'127.0.0.1',port:25577,username:'ZMusicTest',version:'1.8.8',auth:'offline'});
client.on('error',error=>result.errors.push(error.message));
client.on('packet',(data,meta)=>{
    if(meta.name==='custom_payload'&&/^(zmusic|allmusic):channel$/.test(data.channel)&&data.data.subarray(1).toString('utf8').startsWith('[Play]'))result.playPackets++;
    if(meta.name==='chat'&&JSON.stringify(data).includes('片段或版权提示音'))result.restrictionMessage=true;
});
client.once('login',()=>setTimeout(()=>{
    client.write('custom_payload',{channel:'REGISTER',data:Buffer.from('zmusic:channel\0allmusic:channel')});
    client.chat('/zm play kuwo -id:26445261');
},1000));
setTimeout(()=>{
    client.end();fs.writeFileSync('../verification/kuwo-preview-rejected.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));
    process.exitCode=result.playPackets===0&&result.restrictionMessage&&result.errors.length===0?0:1;
},12000);
