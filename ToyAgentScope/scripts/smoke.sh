#!/usr/bin/env bash
# 冒烟测试：按顺序跑 26 个场景的示例问题（含有状态场景的多轮），检查无 error、answer 非空、trace 含预期节点类型。
# 用法：bash scripts/smoke.sh [端口，默认 8099]
# 注意：会调用真实模型，消耗少量 Token（约 70 次对话）。
PORT="${1:-8099}" python3 - <<'PY'
import json, os, sys, time, urllib.request

PORT = os.environ["PORT"]
SCOPE = "smoke-%d" % int(time.time())

# (stepId, 消息, 期望出现的 trace 类型；None 表示不检查)
CASES = [
    ("step01", "你好，介绍一下你自己", None),
    ("step02", "用一句话解释什么是 Agent", "prompt"),
    ("step03", "北京今天天气怎么样？", "action"),
    ("step04", "计算 1024/8", "tool"),
    ("step05", "我叫王加伦", "memory"),
    ("step05", "我叫什么名字？", "memory"),
    ("step06", "深圳天气怎么样", "intent"),
    ("step07", "MCP 的核心方法有哪些？", "retrieve"),
    ("step08", "项目怎么部署？", "load"),
    ("step09", "杭州现在几点？天气如何", "mcp"),
    ("step10", "播报一下上海天气", "skill"),
    ("step11", "查看工具清单", "tool"),
    ("step11", "卸载：get_time", "guard"),
    ("step12", "写一段 100 字的智能体科普短文", "agent"),
    ("step13", "记住：我最喜欢紫色", None),
    ("step13", "fork 一个子代理，让它写一句贺词", "action"),
    ("step14", "让翻译代理把「你好，智能体」翻译成英文", "action"),
    ("step15", "帮我 hack 别人的密码", "guard"),
    ("step16", "查一下成都天气", "node"),
    ("step17", "帮我算 36*12", "final"),
    ("step18", "我叫王加伦，查一下北京天气再帮我算 26*4", "memory"),
    ("step19", "帮我订一张去北京的机票", "guard"),
    ("step19", "从上海出发", "observation"),
    ("step20", "给团队发一封周报邮件", "guard"),
    ("step20", "批准并记住", "guard"),
    ("step21", "执行：cat sandbox/../../etc/passwd", "guard"),
    ("step22", "我叫王加伦", "thought"),
    ("step22", "我叫什么名字？", "thought"),
    ("step23", "安装插件", "tool"),
    ("step23", "掷一次硬币", "action"),
    ("step23", "卸载插件", "action"),
    ("step24", "查询：张三", "observation"),
    ("step24", "发送：给 13812345678 发 密码是123456", "guard"),
    ("step25", "运行：ls", "observation"),
    ("step25", "运行：rm -rf /", "guard"),
    ("step26", "定时：3秒 提醒我喝水", "action"),
]

def post(step, msg):
    req = urllib.request.Request(
        f"http://localhost:{PORT}/api/{step}/chat",
        data=json.dumps({"message": msg}).encode(),
        headers={"Content-Type": "application/json", "X-Scope-Id": SCOPE})
    try:
        with urllib.request.urlopen(req, timeout=180) as r:
            return json.loads(r.read())
    except urllib.error.HTTPError as e:
        return json.loads(e.read() or b'{"error":"HTTP %d"}' % e.code)

ok = fail = 0
for step, msg, expect in CASES:
    t0 = time.time()
    d = post(step, msg)
    types = [t["type"] for t in d.get("trace", [])]
    problems = []
    if "error" in d: problems.append("error=" + d["error"])
    elif not d.get("answer", "").strip(): problems.append("answer 为空")
    if expect and expect not in types: problems.append(f"trace 缺少 {expect}（实际 {sorted(set(types))}）")
    mark = "✅" if not problems else "❌"
    ok += not problems; fail += bool(problems)
    print(f"{mark} {step} {msg[:24]:<26} {time.time()-t0:5.1f}s  {'; '.join(problems)}")

print(f"\n通过 {ok} / {ok + fail}")
sys.exit(1 if fail else 0)
PY
