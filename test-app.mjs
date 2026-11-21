// Automated phone test: drives the installed app on an Android emulator and writes test-report/
import { execSync } from 'node:child_process';
import fs from 'node:fs';

const PKG = 'com.me.todoapp', OUT = 'test-report';
fs.mkdirSync(OUT, { recursive: true });
const sh = c => { try { return execSync(c, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }); } catch (e) { return (e.stdout || '') + (e.stderr || ''); } };
const sleep = ms => new Promise(r => setTimeout(r, ms));
const results = [], jsErrors = [];
let shotN = 0;
const shot = name => { const f = `${OUT}/${String(++shotN).padStart(2, '0')}-${name}.png`; try { execSync(`adb exec-out screencap -p > "${f}"`); } catch (e) {} };
const prefs = f => sh(`adb shell run-as ${PKG} cat shared_prefs/${f}.xml`);
const inFront = () => sh(`adb shell "dumpsys activity activities | grep -E 'ResumedActivity'"`).includes(PKG);
const ok = (c, msg) => { if (!c) throw new Error(msg); };

let ws, msgId = 0;
const pending = new Map();
function send(method, params = {}) {
  const id = ++msgId;
  ws.send(JSON.stringify({ id, method, params }));
  return new Promise((res, rej) => { pending.set(id, res); setTimeout(() => { if (pending.has(id)) { pending.delete(id); rej(new Error('timeout ' + method)); } }, 15000); });
}
async function js(expr) {
  const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true });
  if (r.result && r.result.exceptionDetails) throw new Error('JS: ' + (r.result.exceptionDetails.exception?.description || r.result.exceptionDetails.text));
  return r.result?.result?.value;
}
async function connect() {
  if (ws) { try { ws.close(); } catch (e) {} ws = null; }
  for (let i = 0; i < 45; i++) {
    const pid = sh(`adb shell pidof ${PKG}`).trim().split(/\s+/)[0];
    if (pid) {
      sh('adb forward --remove-all');
      sh(`adb forward tcp:9222 localabstract:webview_devtools_remote_${pid}`);
      try {
        const list = await (await fetch('http://127.0.0.1:9222/json')).json();
        const p = list.find(x => x.type === 'page' && x.webSocketDebuggerUrl);
        if (p) {
          ws = new WebSocket(p.webSocketDebuggerUrl);
          await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
          ws.onmessage = ev => {
            const m = JSON.parse(ev.data);
            if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
            else if (m.method === 'Runtime.exceptionThrown') jsErrors.push(m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text);
            else if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') jsErrors.push(m.params.args.map(a => a.value ?? a.description).join(' '));
          };
          await send('Runtime.enable');
          for (let j = 0; j < 60; j++) { if (await js('!!window.__app')) { await sleep(800); return; } await sleep(500); }
          throw new Error('App screen never finished loading');
        }
      } catch (e) { if (String(e.message).includes('never finished')) throw e; }
    }
    await sleep(1000);
  }
  throw new Error('Could not connect to the app');
}
async function launch() { sh(`adb shell am start -W -n ${PKG}/.MainActivity`); await connect(); }
async function test(name, fn) {
  try { const note = await fn(); results.push(['PASS', name, note || '']); }
  catch (e) { results.push(['FAIL', name, e.message]); shot('FAIL-' + name.replace(/\W+/g, '-')); }
  console.log(results[results.length - 1].join(' | '));
}
const findTask = re => js(`JSON.stringify(__app.state.tasks.find(t => /${re}/.test(t.title)) || null)`).then(JSON.parse);

(async () => {
  await test('App opens', async () => {
    await launch();
    shot('home');
    return `${await js('__app.state.tasks.length')} tasks on first open`;
  });

  await test('Add task with smart typing', async () => {
    await js('__app.openNew()'); await sleep(600);
    await js(`__app.setState(s => ({ form: { ...s.form, title: 'Send report tomorrow 3pm #Finance' } }))`); await sleep(500);
    shot('add-sheet-typed');
    await js('__app.addTask()'); await sleep(1200);
    const t = await findTask('Send report');
    ok(t, 'Task was not added');
    ok(t.time === '15:00', `Time should be 15:00, got ${t.time}`);
    ok(JSON.stringify(t).includes('Finance'), 'Project #Finance not detected');
    shot('after-add');
    return `title="${t.title}" time=${t.time}`;
  });

  await test('Tasks survive closing the app', async () => {
    await sleep(1500);
    ok(prefs('store').includes('Send report'), 'Task not written to phone storage');
    sh(`adb shell am force-stop ${PKG}`); await sleep(1500);
    await launch();
    ok(await findTask('Send report'), 'Task disappeared after reopening');
    shot('after-restart');
  });

  await test('Back button goes Home first', async () => {
    await js(`__app.setState({ screen: 'list' })`); await sleep(600);
    sh('adb shell input keyevent 4'); await sleep(1000);
    ok(await js('__app.scr()') === 'home', `Still on ${await js('__app.scr()')}`);
  });

  await test('Back button closes Add task sheet', async () => {
    await js('__app.openNew()'); await sleep(600);
    sh('adb shell input keyevent 4'); await sleep(1000);
    ok(!(await js('__app.state.sheet')), 'Sheet still open');
  });

  await test('Back on Home leaves the app', async () => {
    sh('adb shell input keyevent 4'); await sleep(1500);
    ok(!inFront(), 'App is still in front');
    await launch();
    ok(await findTask('Send report'), 'Tasks lost after back-out');
  });

  await test('Follows phone dark mode', async () => {
    await js('__app.setState({ dark: null })');
    sh('adb shell cmd uimode night yes'); await sleep(2500);
    const d = await js('__app.isDark()'); shot('dark-mode');
    sh('adb shell cmd uimode night no'); await sleep(2500);
    const l = await js('__app.isDark()');
    ok(d === true && l === false, `dark=${d} after night on, dark=${l} after night off`);
  });

  await test('Morning summary scheduled', async () => {
    ok(prefs('reminders').includes('Good morning'), 'No 9:00 AM summary in schedule');
  });

  let notifyOk = false;
  await test('Reminder notification arrives', async () => {
    await js('__app.openNew()'); await sleep(500);
    await js(`__app.setState(s => ({ form: { ...s.form, title: 'Notify test' } }))`); await sleep(300);
    await js('__app.addTask()'); await sleep(800);
    const t = await findTask('Notify test'); ok(t, 'Could not add test task');
    await js(`(() => { const d = new Date(Date.now() + 120000); __app.upd(${t.id}, { day: __app.state.selDay, rem: 0, time: String(d.getHours()).padStart(2,'0') + ':' + String(d.getMinutes()).padStart(2,'0') }); })()`);
    await sleep(2000);
    ok(prefs('reminders').includes('Notify test'), 'Reminder was not scheduled');
    sh('adb shell input keyevent 3');
    for (let i = 0; i < 40 && !notifyOk; i++) { await sleep(5000); notifyOk = sh('adb shell dumpsys notification --noredact').includes('Notify test'); }
    sh('adb shell cmd statusbar expand-notifications'); await sleep(1500); shot('notification'); sh('adb shell cmd statusbar collapse');
    ok(notifyOk, 'No notification within 3 minutes (app closed)');
  });

  await test('Widget data + tick from widget', async () => {
    await launch();
    ok(prefs('widget').includes('Send report'), 'Widget has no task data');
    const t = await findTask('Send report');
    sh('adb shell input keyevent 3'); await sleep(800);
    sh(`adb shell am broadcast -n ${PKG}/.WidgetMedium -a ${PKG}.TICK --ei id ${t.id}`); await sleep(1500);
    await launch(); await sleep(1500);
    const t2 = await findTask('Send report');
    ok(t2 && t2.done, 'Task ticked on widget is not done in app');
  });

  await test('Focus timer schedules finish alert', async () => {
    const t = await findTask('Notify test');
    await js(`__app.startFocus(${t.id})`); await sleep(2000);
    ok(prefs('reminders').includes('Focus session complete'), 'Focus alert not scheduled');
    shot('focus');
    await js('__app.setState({ focus: null })'); await sleep(1500);
    ok(!prefs('reminders').includes('Focus session complete'), 'Focus alert not cancelled after stopping');
  });

  await test('No crashes', async () => {
    const log = sh('adb logcat -d -b crash');
    fs.writeFileSync(`${OUT}/crash-log.txt`, log);
    ok(!log.includes(PKG), 'App crashed – see crash-log.txt');
  });

  await test('No JavaScript errors', async () => { ok(!jsErrors.length, jsErrors.slice(0, 5).join(' || ')); });

  fs.writeFileSync(`${OUT}/logcat.txt`, sh('adb logcat -d'));
  const fails = results.filter(r => r[0] === 'FAIL').length;
  const report = `TODO APP – PHONE TEST REPORT\n${new Date().toISOString()}\n${results.length - fails} passed, ${fails} failed\n\n` + results.map(r => `${r[0]}  ${r[1]}${r[2] ? '\n      ' + r[2] : ''}`).join('\n') + (jsErrors.length ? `\n\nJS errors:\n${jsErrors.join('\n')}` : '') + '\n';
  fs.writeFileSync(`${OUT}/results.txt`, report);
  console.log('\n' + report);
  try { ws && ws.close(); } catch (e) {}
  process.exit(fails ? 1 : 0);
})().catch(e => { fs.writeFileSync(`${OUT}/results.txt`, 'Test run crashed: ' + e.stack); console.error(e); process.exit(1); });
