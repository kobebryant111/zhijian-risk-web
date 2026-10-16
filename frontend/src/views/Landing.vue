<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

function goLogin() {
  router.push(auth.isLogin ? '/app' : '/login')
}
</script>

<template>
  <div class="page">
    <header class="nav">
      <div class="brand">
        <span class="logo">鉴</span>
        <strong>智鉴风控官</strong>
      </div>
      <nav class="links">
        <a href="#features">功能</a>
        <a href="#orgs">组织</a>
        <a href="#secure">安全</a>
      </nav>
      <button class="btn-dark" type="button" @click="goLogin">
        {{ auth.isLogin ? '进入工作台' : '登录' }}
      </button>
    </header>

    <main class="hero">
      <section class="left">
        <div class="mark">鉴</div>
        <h1>合作机构风险，值得一套可登录的智鉴工作台</h1>
        <p class="lead">
          多组织隔离 · 名单与报告分属各部门 · 登录后协同监测准入与吹哨。
          外网验证、内网可迁，数据不再散落本机。
        </p>

        <div class="login-card">
          <div class="card-copy">
            <div class="eyebrow">Web 工作台</div>
            <h3>登录后进入你的组织空间</h3>
            <p>机构名单、成员权限、监测报告按组织隔离，同事看到同一套数据。</p>
          </div>
          <button class="btn-dark wide" type="button" @click="goLogin">
            {{ auth.isLogin ? '进入工作台' : '立即登录' }}
          </button>
        </div>

        <ul class="checks">
          <li><i>✓</i> 组织级数据隔离</li>
          <li><i>✓</i> 创建组织 / 拉人入组</li>
          <li><i>✓</i> 机构名单按组织分管</li>
        </ul>
      </section>

      <section class="right" id="features">
        <div class="phone p1">
          <div class="phone-title">总览</div>
          <div class="mini-card">昨日有效 <b>12</b></div>
          <div class="mini-card warn">L1 致命 <b>2</b></div>
          <div class="mini-card">待复核 <b>5</b></div>
        </div>
        <div class="phone p2">
          <div class="phone-title">组织空间</div>
          <div class="row-card">合作机构组 · 8 人</div>
          <div class="row-card">运营辅助组 · 3 人</div>
          <div class="row-card soft">+ 创建组织</div>
        </div>
        <div class="phone p3">
          <div class="phone-title">机构名单</div>
          <div class="row-card">某某助贷 · 助贷</div>
          <div class="row-card">航卓融担 · 担保</div>
        </div>
      </section>
    </main>

    <section class="band" id="orgs">
      <h2>一个部门一套机构与报告</h2>
      <p>组织 A 的名单与跑批结果，组织 B 默认不可见。同组织成员共享工作台。</p>
    </section>

    <section class="band soft" id="secure">
      <h2>登录 · 角色 · 审计</h2>
      <p>平台管理员开通组织；组织管理员拉人赋权；经办与复核各司其职。</p>
    </section>
  </div>
</template>

<style scoped>
.page {
  min-height: 100%;
  background:
    radial-gradient(ellipse 80% 50% at 10% 20%, rgba(229,122,58,0.08), transparent),
    linear-gradient(180deg, #f7f2ea 0%, #efe7db 100%);
}
.nav {
  max-width: 1180px;
  margin: 0 auto;
  padding: 22px 28px;
  display: flex;
  align-items: center;
  gap: 24px;
}
.brand { display: flex; align-items: center; gap: 10px; font-size: 18px; }
.logo, .mark {
  width: 36px; height: 36px; border-radius: 50%;
  background: var(--accent); color: white;
  display: grid; place-items: center; font-weight: 700;
}
.mark { width: 56px; height: 56px; font-size: 22px; margin-bottom: 18px; }
.links { display: flex; gap: 22px; margin-left: auto; color: var(--muted); font-size: 14px; font-weight: 500; }
.btn-dark {
  border: 0; border-radius: 999px; background: var(--btn); color: #fff;
  padding: 10px 20px; font-weight: 600; cursor: pointer;
}
.btn-dark:hover { background: var(--btn-hover); }
.btn-dark.wide { width: 100%; padding: 14px 20px; margin-top: 8px; }
.hero {
  max-width: 1180px; margin: 0 auto; padding: 24px 28px 64px;
  display: grid; grid-template-columns: 1.05fr 0.95fr; gap: 36px; align-items: center;
}
.left h1 {
  font-size: clamp(34px, 4.4vw, 52px); line-height: 1.15; margin: 0 0 16px;
  letter-spacing: -0.02em;
}
.lead { color: var(--muted); font-size: 16px; line-height: 1.7; margin: 0 0 28px; max-width: 520px; }
.login-card {
  background: var(--card); border-radius: 32px; padding: 24px;
  box-shadow: var(--shadow); max-width: 420px;
}
.eyebrow { color: var(--accent); font-size: 13px; font-weight: 700; margin-bottom: 8px; }
.login-card h3 { margin: 0 0 8px; font-size: 22px; }
.login-card p { margin: 0; color: var(--muted); font-size: 14px; line-height: 1.6; }
.checks {
  display: flex; flex-wrap: wrap; gap: 18px; list-style: none; padding: 0; margin: 22px 0 0;
  color: var(--muted); font-size: 14px; font-weight: 500;
}
.checks i {
  display: inline-grid; place-items: center; width: 18px; height: 18px; margin-right: 6px;
  border-radius: 50%; background: #e8f5ec; color: var(--ok); font-style: normal; font-size: 11px;
}
.right { position: relative; min-height: 520px; }
.phone {
  position: absolute; width: 220px; background: var(--card); border-radius: 28px;
  padding: 18px; box-shadow: var(--shadow); border: 1px solid var(--line);
}
.p1 { right: 40px; top: 20px; transform: rotate(8deg); z-index: 3; }
.p2 { right: 180px; top: 120px; transform: rotate(-6deg); z-index: 2; }
.p3 { right: 20px; top: 280px; transform: rotate(3deg); z-index: 1; width: 200px; }
.phone-title { font-weight: 700; margin-bottom: 12px; }
.mini-card, .row-card {
  background: #f7f1e8; border-radius: 16px; padding: 12px 14px; margin-bottom: 10px; font-size: 13px;
}
.mini-card b { float: right; }
.mini-card.warn { background: #fde8e4; }
.row-card.soft { color: var(--accent); font-weight: 600; }
.band { max-width: 1180px; margin: 0 auto; padding: 28px 28px 48px; }
.band.soft { padding-bottom: 80px; }
.band h2 { margin: 0 0 8px; font-size: 28px; }
.band p { margin: 0; color: var(--muted); max-width: 640px; line-height: 1.7; }
@media (max-width: 960px) {
  .hero { grid-template-columns: 1fr; }
  .right { min-height: 380px; }
  .links { display: none; }
}
</style>
