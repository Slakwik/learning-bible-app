// Offline corpus/design/source checks. Not a substitute for compiling or running Android.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { test } = require('node:test');
const root = path.resolve(__dirname, '../..');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');
const kotlin = n => read('android/app/src/main/java/ru/spbchurch/biblelearning/' + n + '.kt');
const parser = kotlin('Lesson');
const bodyRegex = parser.match(/val pattern = Regex\("""([\s\S]*?)""",/)[1];
const corpus = fs.readdirSync(path.join(root, '_lessons')).filter(f => f.endsWith('.md'));
test('every existing question parses in source order with exact website IDs', () => {
  let total = 0;
  for (const f of corpus) {
    const raw = read('_lessons/' + f);
    const expected = [...raw.matchAll(/data-question="([^"]+)"/g)].map(m => m[1]);
    const parsed = [...raw.matchAll(new RegExp(bodyRegex, 'gs'))].map(m => m[1]);
    assert.deepEqual(parsed, expected, f);
    assert.equal(new Set(parsed).size, parsed.length, 'duplicate ID: ' + f);
    total += parsed.length;
  }
  console.log('Validated ' + corpus.length + ' lessons and ' + total + ' answer fields');
});
test('front matter has valid unique IDs, titles and positive orders', () => {
  const ids = new Set();
  for (const f of corpus) {
    const front = read('_lessons/' + f).replace(/\r\n/g, '\n').split(/^---\s*$/m, 3)[1];
    const fields = Object.fromEntries(front.split('\n').filter(l => l.includes(':')).map(l =>
      [l.slice(0,l.indexOf(':')).trim(), l.slice(l.indexOf(':')+1).trim().replace(/^"|"$/g, '')]));
    assert.match(fields.slug, /^[a-z0-9-]+$/);
    assert.match(fields.course, /^[a-z0-9-]+$/);
    assert.ok(fields.title);
    assert.ok(Number(fields.order) > 0);
    assert.ok(!ids.has(fields.slug));
    ids.add(fields.slug);
  }
});
function luminance(hex) {
  const c = hex.replace('#', '').match(/../g).map(h => parseInt(h,16)/255).map(x => x <= .04045 ? x/12.92 : ((x+.055)/1.055)**2.4);
  return .2126*c[0]+.7152*c[1]+.0722*c[2];
}
function contrast(a,b) { const x=luminance(a), y=luminance(b); return (Math.max(x,y)+.05)/(Math.min(x,y)+.05); }
test('light and dark text and buttons meet 4.5:1 contrast', () => {
  for (const variant of ['values','values-night']) {
    const xml = read('android/app/src/main/res/'+variant+'/colors.xml');
    const c = Object.fromEntries([...xml.matchAll(/<color name="([^"]+)">([^<]+)<\/color>/g)].map(m=>[m[1],m[2]]));
    for (const [ink,bg] of [['ink','surface'],['muted','surface'],['ink','background'],['muted','background'],['on_primary','primary']]) {
      const ratio = contrast(c[ink],c[bg]);
      assert.ok(ratio >= 4.5, variant+' '+ink+'/'+bg+' '+ratio);
    }
  }
  assert.ok(contrast('#58685E', '#F3EAD8') >= 4.5);
});
test('no WebView, portrait lock, broad storage permissions or cloud backup', () => {
  const manifest = read('android/app/src/main/AndroidManifest.xml');
  assert.doesNotMatch(manifest, /screenOrientation|READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE/);
  assert.match(manifest, /allowBackup="false"/);
  assert.match(manifest, /android:name=".BibleApp"/);
  const sourceDir = path.join(root,'android/app/src/main/java/ru/spbchurch/biblelearning');
  for (const f of fs.readdirSync(sourceDir)) assert.doesNotMatch(fs.readFileSync(path.join(sourceDir,f),'utf8'), /android\.webkit|WebView\(/);
});
test('account isolation and three-way merge remain explicit', () => {
  assert.match(kotlin('AnswerStore'), /PRIMARY KEY\(uid, slug\)/);
  assert.match(kotlin('Sync'), /runTransaction/);
  assert.match(kotlin('Sync'), /Source\.SERVER/);
  assert.match(kotlin('Sync'), /q\[0-9\]\+\[a-z\]\*/);
  assert.match(kotlin('AnswerStore'), /current\.revision == sent\.revision/);
  assert.match(kotlin('App'), /setPersistenceEnabled\(false\)/);
});
test('activities declared and UI uses dp, insets and accessible controls', () => {
  const manifest = read('android/app/src/main/AndroidManifest.xml');
  for (const n of ['MainActivity','LessonActivity','SettingsActivity','LoginActivity']) assert.ok(manifest.includes('android:name=".'+n+'"'));
  assert.match(kotlin('Ui'), /WindowInsetsCompat\.Type\.ime/);
  assert.match(kotlin('Ui'), /minHeight = dp\(48\)/);
  assert.match(kotlin('LessonActivity'), /doAfterTextChanged/);
});
test('deleting downloaded content cannot delete answer databases', () => {
  assert.match(parser, /fun clearDownloaded\(\) \{ cache\.delete\(\) \}/);
  assert.doesNotMatch(kotlin('SettingsActivity'), /deleteDatabase|deleteAll|deleteRecursively/);
});
