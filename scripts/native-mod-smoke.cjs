// 玩家协议连接下发的 URL 直接送入官方模组 Java 核心及官方原生库，不记录签名 URL。
const mc = require('minecraft-protocol');
const fs = require('node:fs');
const {spawn} = require('node:child_process');
const readline = require('node:readline');
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
const version = process.argv[2] || '1.8.8';
const port = Number(process.argv[3] || 25577);
const output = process.argv[4] || 'native-mod-result.json';
const lab = process.env.ZMUSIC_NATIVE_LAB || 'E:\\Zhang\\ZMusic-Audio-Lab';
const java = process.env.ZMUSIC_TEST_JAVA || 'C:\\Program Files\\Zulu\\zulu-21\\bin\\java.exe';
const result = {version, engine:'official ZMusicPlayer + 1.0.0-alpha.7 native', sources:{}, errors:[]};
let stage;
const bridge = spawn(java, ['-Dfile.encoding=UTF-8', `-Djava.library.path=${lab}\\official-player-windows`,
    '-cp', `${lab}\\official-core;${lab}\\log4j-api.jar`, 'NativeModBridge'], {stdio:['pipe','pipe','pipe']});
readline.createInterface({input:bridge.stdout}).on('line', line => {
    if (!line.startsWith('{')) return;
    try {const event=JSON.parse(line); const record=result.sources[event.source]; if(record) record[event.operation]=event;} catch { }
});
bridge.stderr.on('data', data => {
    // 原生库错误只记录类别，避免官方 INFO 日志意外含有媒体地址。
    if (/Exception|UnsatisfiedLinkError/.test(data.toString())) result.errors.push('Native bridge error');
});
bridge.on('error', () => result.errors.push('Native bridge could not start'));
const client = mc.createClient({host:'127.0.0.1',port,username:'ZMusicTest',version,auth:'offline'});
client.on('error', error => result.errors.push(error.message));
function text(component) {
    if (typeof component === 'string') return component;
    if (Array.isArray(component)) return component.map(text).join('');
    return component ? (component.text || '') + text(component.extra || []) : '';
}
client.on('packet', (data, meta) => {
    if (!stage) return;
    const record=result.sources[stage];
    if (meta.name === 'custom_payload' && data.channel === 'zmusic:channel') {
        const message=data.data.subarray(1).toString('utf8');
        if(message.startsWith('[Play]')) {
            record.playPackets++;
            bridge.stdin.write(`PLAY ${stage} ${message.slice(6)}\n`);
        }
        if(message === '[Stop]') bridge.stdin.write(`STOP ${stage}\n`);
        if(message.startsWith('[Lyric]') && message.slice(7).trim()) record.lyricMessages++;
    }
    if (meta.name === 'chat' && data.position === 2) {
        try {if(text(JSON.parse(data.message)).trim()) record.lyricMessages++;} catch { }
    }
});
let finished=false;
async function finish() {
    if(finished)return;finished=true;client.end();bridge.stdin.end();
    await wait(300);
    fs.writeFileSync(output,JSON.stringify(result,null,2)); console.log(JSON.stringify(result,null,2));
    const passed=Object.entries(result.sources).every(([source,r])=>r.playPackets>0&&r.play?.state===2&&r.play.positionMs>=1000
        &&!r.play.hasError&&r.check?.state===2&&r.check.positionMs>=12000&&!r.check.hasError&&r.stop?.state===0&&(source==='bilibili'||r.lyricMessages>0));
    process.exitCode=passed&&Object.keys(result.sources).length===5&&result.errors.length===0?0:1;
}
client.once('login', async () => {
    try {
        await wait(1500);
        client.write('custom_payload',{channel:'REGISTER',data:Buffer.from('zmusic:channel\0allmusic:channel\0AudioBuffer')});
        const commands={
            '163':'/zm play 163 -id:2652820720',
            'qq':'/zm play qq -id:001snXa70bWkPm,001snXa70bWkPm',
            'kuwo':'/zm play kuwo -id:51685512',
            'kugou':'/zm play kugou -id:1bc927f73529ea92ce6fc50a34febeca,172378798,8830386150',
            'bilibili':'/zm play bilibili BV1GJ411x7h7'
        };
        for(const [source,command] of Object.entries(commands)) {
            stage=source;result.sources[source]={playPackets:0,lyricMessages:0};client.chat(command);
            const deadline=Date.now()+60000;
            while(Date.now()<deadline && !result.sources[source].play)await wait(200);
            await wait(15000);
            bridge.stdin.write(`CHECK ${source}\n`);await wait(500);
            client.chat('/zm stop');await wait(1500);stage=null;
        }
    } catch(error) {result.errors.push(error.message);}
    finally {await finish();}
});
setTimeout(()=>{if(!finished){result.errors.push('Integration timed out');finish();}},360000).unref();
