// 连接本地测试服，验证旧客户端协议、播放、停止和歌词，不模拟声音解码结果。
const mc=require('minecraft-protocol');
const fs=require('node:fs');
const version=process.argv[2]||'1.8.8',port=Number(process.argv[3]||25577);
const output=process.argv[4]||'protocol-result.json';
const result={version,connected:false,playPackets:0,stopPackets:0,lyricPackets:0,actionBarMessages:0,bossBarLyricMessages:0,chatMessages:0,errors:[]};
const client=mc.createClient({host:'127.0.0.1',port,username:'ZMusicTest',version,auth:'offline'});
let commands=false;
function send(text){if(client.chat)client.chat(text);else client.write('chat',{message:text});}
client.on('login',()=>{result.connected=true;});
client.on('packet',(data,meta)=>{
 if(meta.name==='boss_bar'&&data.action===3&&data.title)result.bossBarLyricMessages++;
 if(meta.name==='custom_payload'){
  const channel=data.channel||'';
  if(channel==='zmusic:channel'||channel==='allmusic:channel'){
   const text=data.data.subarray(1).toString('utf8');
   if(text.startsWith('[Play]'))result.playPackets++;
   if(text.startsWith('[Stop]'))result.stopPackets++;
   if(text.startsWith('[Lyric]')&&text.length>7)result.lyricPackets++;
  }
 }
 if(meta.name==='chat'||meta.name==='system_chat'){
  result.chatMessages++;
  if(data.position===2)result.actionBarMessages++;
 }
 if(meta.name==='position'&&!commands){commands=true;
  const registration=Buffer.from('zmusic:channel\0allmusic:channel\0AudioBuffer');
  client.write('custom_payload',{channel:Number(version.split('.')[1])<=12?'REGISTER':'minecraft:register',data:registration});
  setTimeout(()=>send('/zm play 163 -id:2652820720'),1000);
  setTimeout(()=>send('/zm stop'),18000);
 }
});
client.on('error',e=>result.errors.push(e.message));
client.on('kick_disconnect',e=>result.errors.push(JSON.stringify(e)));
setTimeout(async()=>{
 fs.writeFileSync(output,JSON.stringify(result,null,2));client.end();
 process.exitCode=result.connected&&result.playPackets>0&&result.errors.length===0?0:1;
},21000);
