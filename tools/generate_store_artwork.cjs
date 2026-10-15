/* Rebuild editable SVG layouts and RGB PNG exports from real Android UI captures.
 * Usage: NODE_PATH=<node_modules containing sharp> node tools/generate_store_artwork.cjs
 */
const fs = require('fs');
const path = require('path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '..');
const out = path.join(root, 'store/google-play/en-US/graphics');
const escape = value => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;');
const shots = [
  ['01-today', ['Less rambling.', 'More impact.'], 'Make your point from the first sentence.', true],
  ['02-programme', ['Build a', 'sharper voice.'], '14 focused chapters. One skill at a time.', false],
  ['03-lesson', ['Turn a thought into', 'a clear answer.'], 'See an example. Make it your own.', false],
  ['04-compare', ['Listen. Refine.', 'Try again.'], 'Compare two attempts at the same question.', true, true],
  ['05-disagreement', ['Hold your ground.', 'Keep your cool.'], 'Practise disagreeing with clarity.', false],
  ['06-progress', ['Hear your', 'progress.'], 'Revisit your starting point and saved answers.', false, true],
];
const svg = (w,h,body) => `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">${body}</svg>`;
const text = (x,y,label,size,fill,font='Arial',extra='') => `<text x="${x}" y="${y}" font-family="${font}" font-size="${size}" fill="${fill}" ${extra}>${escape(label)}</text>`;
async function save(name, source) {
  fs.writeFileSync(path.join(out,'source',name+'.svg'), source);
  await sharp(Buffer.from(source)).flatten({background:'#F6F5F0'}).removeAlpha().toColourspace('srgb').png().toFile(path.join(out,name+'.png'));
}
async function main() {
  fs.mkdirSync(path.join(out,'source'), {recursive:true});
  for (const [name, lines, sub, dark, sample] of shots) {
    const bg=dark?'#285D4E':'#E5EBE2', ink=dark?'#F6F5F0':'#203A35', quiet=dark?'#CDDBCF':'#50685C';
    const raw=fs.readFileSync(path.join(out,'raw',name+'.png')).toString('base64');
    let body=`<rect width="1080" height="1920" fill="${bg}"/>`;
    body+=text(90,57,'SAYSHARP',25,quiet,'Arial','letter-spacing="5" font-weight="bold"');
    body+=text(990,57,name.slice(0,2)+' / 06',23,quiet,'Arial','text-anchor="end"');
    body+=text(85,145,lines[0],76,ink,'Georgia');
    body+=text(85,230,lines[1],76,ink,'Georgia');
    body+=text(90,291,sub,32,quiet);
    // Proportional UI image, no stretching, overlays, or replacement interface elements.
    body+=`<defs><clipPath id="screen"><rect x="90" y="342" width="900" height="1530" rx="28"/></clipPath></defs>`;
    body+=`<rect x="88" y="340" width="904" height="1534" rx="30" fill="${dark?'#527B65':'#CAD4C7'}"/>`;
    body+=`<image x="90" y="342" width="900" height="1530" clip-path="url(#screen)" xlink:href="data:image/png;base64,${raw}"/>`;
    if(sample) body+=text(989,1905,'Sample practice',21,quiet,'Arial','text-anchor="end"');
    await save(name,svg(1080,1920,body));
  }
  let feature=`<rect width="1024" height="500" fill="#285D4E"/>
    <circle cx="1055" cy="-37" r="220" fill="#316654"/>
    <circle cx="1020" cy="523" r="175" fill="none" stroke="#46785F" stroke-width="2"/>
    <circle cx="1020" cy="523" r="214" fill="none" stroke="#46785F" stroke-width="2"/>
    <path d="M0 438 C145 475 150 390 280 450" fill="none" stroke="#46785F" stroke-width="2"/>`;
  feature+=text(76,103,'saysharp',29,'#D3DECF','Arial','font-weight="bold" letter-spacing="-0.5"');
  feature+=text(71,208,'Make every',66,'#F6F5F0','Georgia');
  feature+=text(71,286,'answer count.',66,'#F6F5F0','Georgia');
  feature+=text(76,343,'Focused speaking practice.',26,'#D3DECF');
  feature+=`<path d="M641 167 H919 Q943 167 943 191 V255 Q943 279 919 279 H688 L652 303 V279 H641 Q617 279 617 255 V191 Q617 167 641 167Z" fill="#F6F5F0"/>
    <path d="M710 313 H912 Q932 313 932 333 V377 Q932 397 912 397 H884 L909 420 L862 397 H710 Q690 397 690 377 V333 Q690 313 710 313Z" fill="#C7D6C0"/>`;
  feature+=text(650,236,'My view is…',32,'#203A35','Georgia');
  feature+=text(717,366,'Here’s why.',28,'#203A35','Georgia');
  await save('feature-graphic',svg(1024,500,feature));
  // Contact sheet is a review aid; upload the individual full-resolution assets.
  const thumbs=await Promise.all(shots.map(async([name],i)=>({input:await sharp(path.join(out,name+'.png')).resize(324,576).toBuffer(),left:28+(i%3)*348,top:Math.floor(i/3)*618+28})));
  thumbs.push({input:await sharp(path.join(out,'feature-graphic.png')).resize(1024,500).toBuffer(),left:24,top:1270});
  await sharp({create:{width:1072,height:1794,channels:3,background:'#D4DDD1'}}).composite(thumbs).png().toFile(path.join(out,'contact-sheet.png'));
  for (const name of [...shots.map(s=>s[0]),'feature-graphic']) {
    const info=await sharp(path.join(out,name+'.png')).metadata();
    if(info.hasAlpha || info.space!=='srgb' || info.width!==(name==='feature-graphic'?1024:1080) || info.height!==(name==='feature-graphic'?500:1920)) throw Error('Invalid export: '+name);
    console.log(`${name}: ${info.width}×${info.height}, RGB PNG`);
  }
}
main().catch(e=>{console.error(e);process.exit(1)});
