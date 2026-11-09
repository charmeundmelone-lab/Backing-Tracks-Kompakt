// Vergleicht Mitschnitt (float32 stereo) mit Erwartung aus Originaldateien.
// Aufruf: node vgl.js mit.f32 L:datei[*g],datei... R:datei...   (Stereo-Datei: STEREO:datei)
const RATE=+(process.env.RATE||48000);const fs=require('fs');const args=process.argv.slice(2);const m=fs.readFileSync(args[0]);const nM=m.length/8;
function wav(p){const o=fs.readFileSync(p);let q=12,f,d;while(q<o.length-8){const id=o.toString('ascii',q,q+4),len=o.readUInt32LE(q+4);if(id==='fmt ')f={ch:o.readUInt16LE(q+10),sr:o.readUInt32LE(q+12),bits:o.readUInt16LE(q+22)};if(id==='data'){d=q+8;break}q+=8+len+(len&1)}
 const bps=f.bits/8,fr=f.ch*bps;const n=Math.floor((o.length-d)/fr);return{f,n,s:(i,c)=>{if(i>=n)return 0;const z=d+i*fr+Math.min(c,f.ch-1)*bps;return bps===3?o.readIntLE(z,3)/8388608:o.readInt16LE(z)/32768}}}
const L=[],R=[];let st=null;
for(const a of args.slice(1)){const [seite,rest]=a.split(/:(.+)/);const w=wav(rest);if(seite==='STEREO')st=w;else (seite==='L'?L:R).push(w);}
function erw(i,c){if(st)return st.s(i,c);const arr=c===0?L:R;let v=0;for(const w of arr)v+=w.s(i,0);return Math.max(-1,Math.min(1,v));}
const mit=(i,c)=>m.readFloatLE(i*8+c*4);
let maxD=0,sumD=0,stellen=0;
for(let s=0;s<Math.floor(nM/RATE)+1;s++){let e=0,a=0,n=0;for(let i=s*RATE;i<Math.min(nM,(s+1)*RATE);i++){for(let c=0;c<2;c++){const d=mit(i,c)-erw(i,c);e+=d*d;a+=erw(i,c)**2;n++;if(Math.abs(d)>maxD)maxD=Math.abs(d);if(Math.abs(d)>1e-4)stellen++;}}
 if(n)console.log('Sek',s,'Pegel',Math.sqrt(a/n).toFixed(4),'Fehler',Math.sqrt(e/n).toExponential(2));}
console.log('Frames',nM,'max Abweichung',maxD.toExponential(2),'Stellen >1e-4:',stellen);
