import { chromium } from 'playwright';
import { readAnswer, readCitations, readReasoning, setDeepThink } from './collect.mjs';
import { shareLink } from './share.mjs';
import { existsSync, mkdirSync, readFileSync, unlinkSync, writeFileSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const configPath = path.join(root, 'config.json');
const mode = process.argv[2] === 'login' ? 'login' : process.argv[2] === 'check' ? 'check' : 'run';

const config = loadConfig();
const state = { jobId: null, accountId: null };

function loadConfig() {
  if (!existsSync(configPath)) {
    throw new Error('缺少 config.json，请先复制 config.example.json');
  }
  const raw = JSON.parse(readFileSync(configPath, 'utf8'));
  if (!raw.baseUrl || !raw.token || !raw.node) {
    throw new Error('config.json 需要 baseUrl、token、node');
  }
  raw.baseUrl = String(raw.baseUrl).replace(/\/$/, '');
  raw.profileRoot = raw.profileRoot || path.join(root, 'profiles');
  raw.pollMs = Number(raw.pollMs) > 0 ? Number(raw.pollMs) : 5000;
  mkdirSync(raw.profileRoot, { recursive: true });
  mkdirSync(path.join(root, 'shots'), { recursive: true });
  return raw;
}

function takeLock() {
  const lockFile = path.join(config.profileRoot, '.worker.lock');
  if (existsSync(lockFile)) {
    const pid = Number(readFileSync(lockFile, 'utf8'));
    if (pid && pid !== process.pid && pidAlive(pid)) {
      throw new Error(`已有采集进程在运行（pid ${pid}）。不要开第二个，否则 Chrome 用户目录会冲突。`);
    }
  }
  writeFileSync(lockFile, String(process.pid));
  const release = () => {
    try {
      if (existsSync(lockFile) && readFileSync(lockFile, 'utf8').trim() === String(process.pid)) {
        unlinkSync(lockFile);
      }
    } catch {
      // 进程退出时锁文件删不掉不影响下次启动判断
    }
  };
  process.on('exit', release);
  process.on('SIGINT', () => {
    release();
    process.exit(0);
  });
}

function pidAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

async function api(pathname, { json, form } = {}) {
  const response = await fetch(config.baseUrl + pathname, {
    method: 'POST',
    headers: {
      'X-Geo-Worker-Token': config.token,
      ...(json ? { 'Content-Type': 'application/json' } : {}),
    },
    body: form || JSON.stringify(json || {}),
  });
  const body = await response.json().catch(() => ({}));
  if (response.status === 401 || response.status === 404) {
    const error = new Error('测试环境还没有本机采集接口。需要先把这次改动发布到测试 ECS，服务起来后再执行。');
    error.fatal = true;
    throw error;
  }
  if (body.code !== 200) {
    const error = new Error(body.msg || `请求失败 HTTP ${response.status}`);
    error.code = body.code;
    error.fatal = body.code === 401;
    throw error;
  }
  return body.data;
}

async function heartbeat() {
  await api('/geo/auto/worker/heartbeat', {
    json: {
      node: config.node,
      version: '1',
      currentJobId: state.jobId,
      currentAccountId: state.accountId,
    },
  });
}

function launchOptions() {
  const options = {
    headless: false,
    viewport: { width: 1440, height: 900 },
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    args: ['--disable-blink-features=AutomationControlled'],
  };
  if (config.executablePath) {
    options.executablePath = config.executablePath;
  } else {
    options.channel = 'chrome';
  }
  return options;
}

function profileDir(accountId) {
  const dir = path.join(config.profileRoot, String(accountId));
  mkdirSync(dir, { recursive: true });
  return dir;
}

async function waitUntilLoggedIn(page) {
  const deadline = Date.now() + 8 * 60 * 1000;
  let stable = 0;
  while (Date.now() < deadline) {
    const url = page.url();
    const onLoginUrl = /sign_in|\/login|passport|from_logout/i.test(url);
    const loginBtn = page
      .locator("[data-testid='to_login_button']")
      .or(page.getByText('登录', { exact: true }))
      .or(page.getByText(/立即登录|扫码登录|请先登录|Sign in/))
      .filter({ visible: true });
    const loginVisible = (await loginBtn.count().catch(() => 0)) > 0;
    const chat = page.locator("textarea, [contenteditable='true']").filter({ visible: true });
    const chatVisible = (await chat.count().catch(() => 0)) > 0;
    if (!loginVisible && !onLoginUrl && chatVisible) {
      stable += 1;
      if (stable >= 3) return true;
    } else {
      stable = 0;
    }
    await page.waitForTimeout(2000);
  }
  return false;
}

async function loginAll() {
  await heartbeat();
  const accounts = await api('/geo/auto/worker/accounts', { json: { node: config.node } });
  if (!accounts?.length) {
    console.log('这台电脑没有可登录的账号。请在平台账号里打开浏览器监测，登录方式选「本机 Chrome」，采集电脑填 ' + config.node);
    return;
  }
  for (const account of accounts) {
    if (!account.url) {
      console.log(`跳过 ${account.platform} ${account.accountLabel}：服务器没有配置提问页地址`);
      continue;
    }
    if (account.loginStatus === 'READY') {
      console.log(`跳过已就绪的 ${account.platform} ${account.accountLabel}`);
      continue;
    }
    console.log(`打开 ${account.platform} ${account.accountLabel}。请在弹出的窗口里完成登录，完成后会自动继续。`);
    const context = await chromium.launchPersistentContext(profileDir(account.accountId), launchOptions());
    try {
      const page = context.pages()[0] || (await context.newPage());
      await page.goto(account.url, { waitUntil: 'domcontentloaded', timeout: 45000 });
      const loggedIn = await waitUntilLoggedIn(page);
      if (!loggedIn) {
        await api('/geo/auto/worker/account-status', {
          json: {
            node: config.node,
            accountId: account.accountId,
            status: 'EXPIRED',
            error: '4 分钟内没有在本机窗口完成登录',
          },
        }).catch(() => {});
        throw new Error(`${account.platform} 尚未登录`);
      }
      await api('/geo/auto/worker/account-status', {
        json: { node: config.node, accountId: account.accountId, status: 'READY' },
      });
      console.log(`已标记就绪：${account.platform} ${account.accountLabel}`);
    } finally {
      await context.close().catch(() => {});
    }
  }
}

async function check() {
  await heartbeat();
  const accounts = await api('/geo/auto/worker/accounts', { json: { node: config.node } });
  console.log(`服务器已接通，电脑名 ${config.node}，分配到 ${(accounts || []).length} 个账号`);
}

async function runLoop() {
  setInterval(() => {
    heartbeat().catch((error) => console.error('心跳失败：' + error.message));
  }, 20000);
  await heartbeat();
  console.log(`采集已启动，电脑名 ${config.node}，等待题目`);
  let idleTicks = 0;
  for (;;) {
    let job = null;
    try {
      job = await api('/geo/auto/worker/claim', { json: { node: config.node } });
    } catch (error) {
      console.error('领取失败：' + error.message);
      if (error.fatal || error.code === 401) process.exit(1);
      await sleep(config.pollMs);
      continue;
    }
    if (!job?.jobId) {
      idleTicks += 1;
      if (idleTicks % 6 === 0) console.log('没有可领的题目，继续等待');
      await sleep(config.pollMs);
      continue;
    }
    idleTicks = 0;
    state.jobId = job.jobId;
    state.accountId = job.accountId;
    console.log(`领取 ${job.platform} #${job.jobId}：${job.question}`);
    try {
      await ask(job);
    } catch (error) {
      console.error(`提问异常 #${job.jobId}：${error.message}`);
      const message = String(error.message || '提问失败').split('\n')[0].slice(0, 400);
      await api('/geo/auto/worker/fail', {
        json: { node: config.node, jobId: job.jobId, reason: 'ERROR', message },
      }).catch((failError) => console.error('回报失败：' + failError.message));
    } finally {
      state.jobId = null;
      state.accountId = null;
    }
    await sleep(3000 + Math.floor(Math.random() * 5000));
  }
}

async function ask(job) {
  const profile = job.profile || {};
  const context = await chromium.launchPersistentContext(profileDir(job.accountId), launchOptions());
  try {
    const page = context.pages()[0] || (await context.newPage());
    await page.goto(profile.url, { waitUntil: 'domcontentloaded', timeout: job.navigationTimeoutMs || 45000 });
    await page.waitForTimeout(1200);
    await dismiss(page, profile.dismissSelectors);
    if (await loginWall(page, profile.loginRequiredSelectors)) {
      await api('/geo/auto/worker/fail', {
        json: {
          node: config.node,
          jobId: job.jobId,
          reason: 'NEED_LOGIN',
          message: `${job.platform} 出现登录或验证，请在这台电脑重新执行 npm run login`,
        },
      });
      console.log(`${job.platform} 需要重新登录，本题已放回队列`);
      return;
    }
    const thinkReady = await setDeepThink(page, !!job.deepThink, profile);
    console.log(
      `${job.platform} 深度思考${job.deepThink ? '开启' : '关闭'}${thinkReady ? '' : '（页面上没找到对应开关）'}，截图${job.screenshot ? '开启' : '关闭'}`,
    );
    await clickVisibleInput(page, profile.inputSelector || 'textarea');
    await page.keyboard.type(job.question, { delay: 35 + Math.floor(Math.random() * 45) });
    await page.waitForTimeout(400);
    if (profile.submitSelector) {
      await page.locator(profile.submitSelector).first().click({ timeout: 8000 });
    } else {
      await page.keyboard.press('Enter');
    }
    const answer = await waitAnswer(page, profile, job.answerTimeoutMs || 120000);
    if (!answer || answer.length < 30 || rejected(answer, profile.answerRejectPatterns)) {
      throw new Error(answer ? `回答过短或不是正文（${answer.length} 字）` : '等待回答超时');
    }
    const reasoning = await readReasoning(page);
    const citations = await readCitations(page);
    let shot = '';
    if (job.screenshot) {
      shot = path.join(root, 'shots', `${job.jobId}.jpg`);
      mkdirSync(path.dirname(shot), { recursive: true });
      const list = page.locator("[data-testid='message-list']").last();
      if (await list.count().catch(() => 0)) {
        await list.screenshot({ path: shot, type: 'jpeg', quality: 60 });
      } else {
        await page.screenshot({ path: shot, fullPage: false, type: 'jpeg', quality: 60 });
      }
      const size = (await readFile(shot)).length;
      if (size > 8 * 1024 * 1024) {
        await page.screenshot({ path: shot, fullPage: false, type: 'jpeg', quality: 50 });
      }
    }
    const probeUrl = await shareLink(page, profile);
    if (!probeUrl) {
      throw new Error('没有生成公开分享链接，不上传私有对话地址');
    }
    console.log(`公开分享 ${probeUrl}`);
    await upload(job, { answer, reasoning, probeUrl, citations, shot });
    console.log(
      `已回传 #${job.jobId}，回答 ${answer.length} 字，思考 ${reasoning.length} 字，信源 ${citations.length} 条，截图 ${shot ? '有' : '无'}`,
    );
  } finally {
    await context.close().catch(() => {});
  }
}

async function upload(job, payload) {
  const form = new FormData();
  form.set('node', config.node);
  form.set('jobId', String(job.jobId));
  form.set('answerText', payload.answer);
  form.set('reasoningText', payload.reasoning || '');
  form.set('probeUrl', payload.probeUrl || '');
  form.set('citationsJson', JSON.stringify(payload.citations || []));
  form.set('diagnostic', `edge profile ${config.node}`);
  if (payload.shot) {
    const bytes = await readFile(payload.shot);
    form.set('screenshot', new Blob([bytes], { type: 'image/jpeg' }), 'page.jpg');
  }
  await api('/geo/auto/worker/result', { form });
}

async function clickVisibleInput(page, selector) {
  await page.keyboard.press('Escape').catch(() => {});
  let target = page.locator(selector).filter({ visible: true }).last();
  if (!(await target.count().catch(() => 0))) {
    await page.reload({ waitUntil: 'domcontentloaded', timeout: 30000 }).catch(() => {});
    await page.waitForTimeout(1000);
    target = page.locator(selector).filter({ visible: true }).last();
  }
  await target.click({ timeout: 20000 });
}

async function waitAnswer(page, profile, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  let last = '';
  let stable = 0;
  let answer = '';
  while (Date.now() < deadline) {
    const streaming = profile.streamingSelector
      ? await page.locator(profile.streamingSelector).count().catch(() => 0)
      : 0;
    answer = await readAnswer(page);
    if (!streaming && answer && answer === last) stable += 1;
    else stable = 0;
    last = answer;
    if (stable >= 3 && answer.length > 30) return answer;
    await page.waitForTimeout(1000);
  }
  return answer;
}

async function dismiss(page, selectors) {
  for (const sel of selectors || []) {
    const loc = page.locator(sel).first();
    if (await loc.count().catch(() => 0)) {
      if (await loc.isVisible().catch(() => false)) {
        await loc.click({ timeout: 1500 }).catch(() => {});
      }
    }
  }
}

async function loginWall(page, selectors) {
  for (const sel of selectors || []) {
    const loc = page.locator(sel).filter({ visible: true });
    if ((await loc.count().catch(() => 0)) > 0) return true;
  }
  return false;
}

function rejected(answer, patterns) {
  return (patterns || []).some((item) => item && answer.includes(item) && answer.length < item.length + 80);
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

takeLock();
const task = mode === 'login' ? loginAll() : mode === 'check' ? check() : runLoop();
task.catch((error) => {
  console.error(error.message);
  process.exit(1);
});
