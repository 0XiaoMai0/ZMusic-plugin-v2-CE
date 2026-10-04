// 逐个音乐源执行玩家命令；有效歌词须包含实际文本，失败歌曲不得发出播放包。
const mc = require('minecraft-protocol');
const fs = require('node:fs');
const wait = ms => new Promise(resolve => setTimeout(resolve,ms));
const result = {version:'1.8.8', sources:{}, errors:[]};
let stage;
const client = mc.createClient({host:'127.0.0.1',port:25577,username:'ZMusicTest',version:'1.8.8',auth:'offline'});
function text(component) {
    if (typeof component === 'string') return component;
    if (Array.isArray(component)) return component.map(text).join('');
    return component ? (component.text || '') + text(component.extra || []) : '';
}
client.on('error', error => result.errors.push(error.message));
client.on('packet', (data, meta) => {
    if (!stage) return;
    const record = result.sources[stage];
    if (meta.name === 'custom_payload' && /^(zmusic|allmusic):channel$/.test(data.channel)) {
        const message = data.data.subarray(1).toString('utf8');
        if (message.startsWith('[Play]')) record.playPackets++;
    }
    if (meta.name === 'chat' && data.position === 2) {
        try {if (text(JSON.parse(data.message)).trim()) record.lyricMessages++;} catch { }
    }
});
client.once('login', async () => {
    try {
        await wait(1500);
        client.write('custom_payload', {channel:'REGISTER',data:Buffer.from('zmusic:channel\0allmusic:channel\0AudioBuffer')});
        const commands = {
            '163':'/zm play 163 -id:2652820720',
            'qq':'/zm play qq -id:001snXa70bWkPm,001snXa70bWkPm',
            'kuwo':'/zm play kuwo -id:51685512',
            'kugou':'/zm play kugou -id:1bc927f73529ea92ce6fc50a34febeca,172378798,8830386150'
        };
        for (const source of Object.keys(commands)) {
            stage = source;
            result.sources[source] = {playPackets:0,lyricMessages:0};
            client.chat(commands[source]);
            await wait(16000);
            client.chat('/zm stop');
            stage = null;
            await wait(500);
        }
    } catch (error) {result.errors.push(error.message);}
    finally {
        client.end();
        fs.writeFileSync('source-packets-result.json', JSON.stringify(result,null,2));
        console.log(JSON.stringify(result,null,2));
        process.exitCode = result.errors.length === 0 && ['163','qq','kuwo','kugou'].every(source => result.sources[source]?.playPackets > 0 && result.sources[source]?.lyricMessages > 0) ? 0 : 1;
    }
});
setTimeout(() => {if (!stage && Object.keys(result.sources).length === 0) {result.errors.push('Login timed out');client.end();process.exitCode=1;}},20000);
