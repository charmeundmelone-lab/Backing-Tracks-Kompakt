// Liest die renderVals()-Daten einer .dc.html-Datei und gibt sie als JSON aus (für expand.py).
const fs = require('fs');
const src = fs.readFileSync(process.argv[2], 'utf8');
const m = src.match(/<script type="text\/x-dc" data-dc-script[^>]*>([\s\S]*?)<\/script>/);
class DCLogic { constructor() { this.props = {}; this.state = {}; } setState() {} }
const Component = new Function('DCLogic', m[1] + '\nreturn Component;')(DCLogic);
process.stdout.write(JSON.stringify(new Component().renderVals()));
