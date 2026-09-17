import os
import re
import json
import html
import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from openai import OpenAI
from dotenv import load_dotenv
from fastapi.middleware.cors import CORSMiddleware

# 加载 .env 配置文件
load_dotenv()

app = FastAPI(
    title="🛒 游戏导购助手",
    description="按名查价、看本站榜单、搜真实口碑的游戏导购微服务，挂在 GameStream 商城的「AI 助手」页面上",
    version="2.1.0",
)


app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 读取环境变量（真实值写在 game-finder/.env 里，该文件已被 .gitignore 排除）
API_KEY = os.getenv("DEEPSEEK_API_KEY")
BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1")
MODEL_NAME = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
PROXY = os.getenv("PROXY", "")
# Java 应用地址（纯地址、无密钥）。browse_deals 靠它读本应用数据库里的榜单数据。
STREAM_BASE_URL = os.getenv("STREAM_BASE_URL", "http://localhost:8080/ex_final_stream").rstrip("/")
# 调外部 HTTP 接口时统一用这个 UA：Steam 与 DuckDuckGo 都对默认的 python-httpx UA 更挑剔
BROWSER_UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
              "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")

if not API_KEY:
    print("Warning: DEEPSEEK_API_KEY environment variable is not set!")


# 没有密钥时不构造客户端，否则 SDK 会在 import 阶段直接抛错，服务连启动都启动不了
client = OpenAI(api_key=API_KEY, base_url=BASE_URL) if API_KEY else None

class GameQuery(BaseModel):
    user_query: str


# ── 工具 1：按游戏名查 Steam 官方数据 ──────────────────────────────────────

STEAM_SEARCH_URL = "https://store.steampowered.com/api/storesearch/"
STEAM_APP_URL = "https://store.steampowered.com/app/{}"


def search_game(game_name: str) -> dict:
    """
    按游戏名查 Steam 官方 storesearch 接口：纯 JSON，不解析 HTML。

    返回的条目里，价格是「分」，这里换算成元；折扣百分比官方接口没给，由原价与现价算出。
    """
    print(f"[Tool Call] search_game is called with: '{game_name}'")
    try:
        resp = httpx.get(
            STEAM_SEARCH_URL,
            params={"term": game_name, "l": "schinese", "cc": "cn"},
            headers={"User-Agent": BROWSER_UA},
            timeout=10.0,
            follow_redirects=True,
        )
        if resp.status_code != 200:
            return {"success": False, "reason": f"Steam 接口返回 HTTP {resp.status_code}"}

        items = resp.json().get("items") or []
        results = []
        for item in items[:5]:
            app_id = item.get("id")
            if not app_id:
                continue
            entry = {
                "name": item.get("name"),
                "url": STEAM_APP_URL.format(app_id),
                "metascore": item.get("metascore") or None,
            }
            price = item.get("price")
            if price:
                initial = price.get("initial", 0) / 100
                final = price.get("final", 0) / 100
                entry["currency"] = price.get("currency")
                entry["original_price"] = round(initial, 2)
                entry["final_price"] = round(final, 2)
                entry["discount_percent"] = round((initial - final) / initial * 100) if initial > final else 0
            else:
                # 免费游戏、未在国区销售、原声带/工具类条目都没有 price 字段，这里如实说明而不是当作 0 元
                entry["price_note"] = "官方接口未给出价格（免费游戏、未在国区销售或非游戏本体）"
            results.append(entry)

        return {
            "success": len(results) > 0,
            "count": len(results),
            "results": results,
            "reason": None if results else (
                "Steam 没有返回任何条目。换成这款游戏的官方英文名再查一次——实测它对中文名的匹配"
                "时好时坏（「黑神话：悟空」能中，「黑神话悟空」「只狼」就中不了），换名字是同一个查询的"
                "另一次尝试，不算重复调用。"
            ),
        }
    except Exception as e:
        return {"success": False, "reason": str(e)}


# ── 工具 2：读本应用数据库里的榜单 ──────────────────────────────────────────

# 键是给模型选的取值，值是 Java 侧 /games 的动作名与说明
DEAL_CATEGORIES = {
    "discount": ("getAllByDiscount", "折扣榜：按折扣幅度排序"),
    "top_seller": ("getAllByTopSeller", "热销榜：按销量排名排序"),
    "giveaway": ("getGiveawayGames", "喜加一：限时免费与即将免费"),
}


def browse_deals(category: str = "discount", limit: int = 8) -> dict:
    """
    调本应用自己的 /games 接口拿榜单数据。走这里而不是 Steam 官方榜单，是为了让 AI 说的
    优惠与用户在商城里看到的是同一份——不会出现「AI 推荐了折扣页里没有的游戏」。

    返回值是 Java 的 Result{code,message,data} 信封，data 里再套一层 PageInfo{list,...}，所以要拆两层。
    """
    print(f"[Tool Call] browse_deals is called with category='{category}', limit={limit}")

    if category not in DEAL_CATEGORIES:
        return {
            "success": False,
            "reason": f"category 只能是 {sorted(DEAL_CATEGORIES)} 之一，收到的是 '{category}'",
        }
    action, description = DEAL_CATEGORIES[category]
    try:
        limit = max(1, min(int(limit), 20))
    except (TypeError, ValueError):
        limit = 8

    try:
        resp = httpx.get(
            f"{STREAM_BASE_URL}/games",
            params={"action": action, "pageNum": 1, "pageSize": limit},
            timeout=10.0,
        )
        if resp.status_code != 200:
            return {"success": False, "reason": f"{STREAM_BASE_URL}/games 返回 HTTP {resp.status_code}"}

        envelope = resp.json()
        if envelope.get("code") != 200:
            return {"success": False, "reason": f"应用返回 code={envelope.get('code')}: {envelope.get('message')}"}

        rows = (envelope.get("data") or {}).get("list") or []
        games = []
        for row in rows:
            game = {
                "name": row.get("gname"),
                "url": row.get("shopUrl"),
                "platform": row.get("platform"),
                "original_price": row.get("originalPrice"),
                "final_price": row.get("finalPrice"),
                "discount_percent": row.get("discountPercent"),
                "review_tier": row.get("reviewTier"),
                "positive_rate": row.get("positiveRate"),
                "review_count": row.get("reviewCount"),
            }
            # Epic 那边没有评价数据，库里存的是 0。0 条评价不是「0% 好评」，说成好评率会误导，
            # 所以评价数为 0 时把好评率与评价数一起摘掉，而不是原样传出去。
            if not game.get("review_count"):
                game.pop("positive_rate", None)
                game.pop("review_count", None)
            games.append({k: v for k, v in game.items() if v not in (None, "")})

        return {
            "success": len(games) > 0,
            "category": category,
            "category_note": description,
            "count": len(games),
            "games": games,
            "reason": None if games else (
                "本应用数据库这一分类下没有数据。新克隆的库只有建库脚本灌的少量演示数据，"
                "跑过爬虫之后才会有真实榜单——如实告诉用户库里暂时没有，不要用别的数据凑数。"
            ),
        }
    except httpx.ConnectError:
        return {
            "success": False,
            "reason": f"连不上 Java 应用（{STREAM_BASE_URL}）。这个工具依赖应用在线，"
                      "请确认应用已启动、且 .env 里的 STREAM_BASE_URL 与实际部署地址一致。",
        }
    except Exception as e:
        return {"success": False, "reason": str(e)}


# ── 工具 3：真去搜一遍口碑 ──────────────────────────────────────────────────

DUCKDUCKGO_HTML_URL = "https://html.duckduckgo.com/html/"

# 每条结果在返回的 HTML 里是「标题链接 + 摘要」结构，先按标题链接切块、再在块内取摘要，
# 避免两个正则各自 findall 之后下标错位。
_RESULT_BLOCK_SPLIT = re.compile(r'(?=<a[^>]*class="result__a")')
_RESULT_LINK = re.compile(r'<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>', re.S)
_RESULT_SNIPPET = re.compile(r'class="result__snippet"[^>]*>(.*?)</a>', re.S)


def _plain_text(fragment: str) -> str:
    """把一小段 HTML 压成一行纯文本。"""
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", "", fragment))).strip()


def _unwrap_redirect(url: str) -> str:
    """DuckDuckGo 有时把结果包成 //duckduckgo.com/l/?uddg=<编码后的真实地址>，这里还原。"""
    if "duckduckgo.com/l/" in url and "uddg=" in url:
        encoded = url.split("uddg=", 1)[1].split("&", 1)[0]
        url = html.unescape(encoded)
    if url.startswith("//"):
        url = "https:" + url
    return url


def search_reviews(game_name: str) -> dict:
    """
    拿游戏名去搜玩家评测与口碑。

    查询词由本函数拼，不让模型自由发挥——这样查询词一定是中性的，只会含
    「评测 / 口碑 / 玩家评价」这类词，搜回来的也就是评测文章本身。

    走 DuckDuckGo 的 html 端点直接取网页。没用 duckduckgo_search 那个库：它现在把
    html / lite 两个后端在源码里禁用了（只剩 bing），而 bing 那条路取回的页面结构
    已经对不上，实际拿不到任何结果、只会抛异常。见 README「已知问题」。
    """
    print(f"[Tool Call] search_reviews is called with: '{game_name}'")
    query = f"{game_name} 游戏 评测 口碑 玩家评价"
    try:
        resp = httpx.post(
            DUCKDUCKGO_HTML_URL,
            data={"q": query},
            headers={"User-Agent": BROWSER_UA},
            timeout=15.0,
            follow_redirects=True,
            proxy=PROXY or None,
        )
        if resp.status_code != 200:
            return {"success": False, "query": query, "reason": f"搜索返回 HTTP {resp.status_code}"}

        results = []
        for block in _RESULT_BLOCK_SPLIT.split(resp.text)[1:]:
            link = _RESULT_LINK.search(block)
            if not link:
                continue
            snippet = _RESULT_SNIPPET.search(block)
            results.append({
                "title": _plain_text(link.group(2)),
                "url": _unwrap_redirect(link.group(1)),
                "body": _plain_text(snippet.group(1)) if snippet else "",
            })
            if len(results) >= 5:
                break

        return {
            "success": len(results) > 0,
            "query": query,
            "count": len(results),
            "reviews": results,
            "reason": None if results else "搜索没有返回结果",
        }
    except Exception as e:
        return {"success": False, "query": query, "reason": str(e)}


tools = [
    {
        "type": "function",
        "function": {
            "name": "search_game",
            "description": "按游戏名到 Steam 官方商店查这款游戏的现价、原价、折扣幅度和媒体评分，并给出 Steam 商店直达链接。用户报出一个具体游戏名、想知道它多少钱 / 打不打折 / 评分如何 / 值不值得买时用它。Steam 对中文名的匹配时好时坏，用中文名查空时，换成这款游戏的官方英文名再查一次。",
            "parameters": {
                "type": "object",
                "properties": {
                    "game_name": {
                        "type": "string",
                        "description": "游戏的官方名称，中文名或英文名都可以，例如 '艾尔登法环'、'Elden Ring'、'Cyberpunk 2077'。中文名查不到时改用英文名。"
                    }
                },
                "required": ["game_name"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "browse_deals",
            "description": "读取本应用数据库里的折扣榜 / 热销榜 / 喜加一，返回一批游戏及其价格、折扣、好评率与评价等级。用户问「最近有什么好价」「大家在买什么」「有什么免费的」这类没有点名某一款游戏的问题时用它。这份数据与本站商城页面上看到的是同一份。该工具依赖本应用在线。",
            "parameters": {
                "type": "object",
                "properties": {
                    "category": {
                        "type": "string",
                        "enum": ["discount", "top_seller", "giveaway"],
                        "description": "discount=折扣榜（问「有什么好价」）；top_seller=热销榜（问「大家在买什么」）；giveaway=喜加一（问「有什么免费的」）。"
                    },
                    "limit": {
                        "type": "integer",
                        "description": "取几条，默认 8，最多 20。"
                    }
                },
                "required": ["category"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "search_reviews",
            "description": "拿游戏名去网上搜一遍玩家评测与口碑，返回若干条网页的标题、链接与摘要。用户问「某某口碑怎么样」「评价如何」「好不好玩」时用它。这是拿到真实评价的唯一途径，口碑类问题必须先搜再答，不要凭自己的印象直接下结论。",
            "parameters": {
                "type": "object",
                "properties": {
                    "game_name": {
                        "type": "string",
                        "description": "要查口碑的游戏名，中英文都可以，例如 '星空'、'Starfield'。"
                    }
                },
                "required": ["game_name"]
            }
        }
    }
]

# 占位人设：正式设定另行设计（见仓库外的 PENDING.md 第 5 条）。这里只保证「说话像个导购、
# 不胡编数据」两件事，措辞上没有花心思，也不含任何角色梗。
SYSTEM_PROMPT = """你是一个游戏导购助手，挂在一个 Steam 折扣信息站的「AI 助手」页面上。

用户会问你三类问题，你按意图挑工具，三个工具是平级的：

1. 报出一个具体游戏名（问多少钱、打不打折、评分如何、值不值得买）→ 调 search_game，拿 Steam 官方数据回答。
2. 问「最近有什么好价」「大家在买什么」「有什么免费的」这类没有点名某一款游戏的问题 → 调 browse_deals，读本站数据库里的折扣 / 热销 / 喜加一。用户在商城里看到的就是这份数据。
3. 问「某某口碑怎么样」「评价如何」「好不好玩」→ 调 search_reviews，真去搜一遍再回答。

关于工具调用的几条规矩：

- 三个工具没有先后之分。没有「必须先用哪个、搜不到才轮到另一个」这种规定，按用户意图直接选。
- 一次提问里可以调多个工具（比如既要价格又要口碑）；信息够了就直接回答，不要为了凑数反复调。
- 一次提问里所有工具调用加起来最多 6 次，代码侧也在数着，超了就会被拒。这是成本上限，不是对你的限制
  ——用户想继续问，他会再发一条消息，配额是每次提问重新算的。信息够了就直接回答，别为了凑数反复调。
- 工具返回 success=false 时，别换个说法再试同一个工具。要么换另一个工具，要么把「查不到」如实告诉用户。
  只有一种例外值得再试一次：用中文名查 Steam 查空了，改用这款游戏的官方英文名再查一遍——Steam 对
  中文名的匹配时好时坏，这不是「换个说法」，是同一个查询的另一次尝试。

关于事实：

- 价格、折扣、评分、口碑，只在工具结果里有依据时才说。工具没给的数据就说没查到，不要凭印象编一个。
- 口碑类回答要落在 search_reviews 搜回来的条目上：说清楚你依据的是哪几条（写出标题）。风评的历史
  沿革、行业背景这类常识可以补，但那是补充，不能替代搜到的东西，两者要让用户分得出来。
- 面向用户的回答里不要出现你自己写出来的链接。要给出链接时，只能用工具返回值里的 url 字段。
- 榜单数据可能为空（新克隆的库里只有少量演示数据）。空就说空，不要拿别的数据凑数。

关于底线：

- 不提供盗版、破解、外挂，也不提供任何绕过正版授权的下载方式。用户明确索要这类东西时礼貌拒绝，
  并告诉他：这些帮不了，但可以帮他找正版好价。

输出格式：

- 只输出一个 JSON 字符串。不要 markdown 代码块，不要 JSON 之外的解释文字。
- 结构固定为：{"success": boolean, "game_name": string_or_null, "game_url": string_or_null, "message": string}
- message 是给用户看的完整回答，用户只会看到这一段文字，所以结论（价格、折扣、评分、口碑）都要写进这里。
  **它是纯文本**——页面按纯文本渲染，只认换行符：不要用 `**`、`#`、`- `、`|` 这些 markdown 标记，
  也不要用表格或代码块，否则用户会看到一堆星号和井号。要强调就直接写「重点」两个字，不要写「**重点**」；
  要分点就写「1. 2. 3.」或者「第一、第二」，不要写「- 」。
  message 里要换行时，写成 `\n`（反斜杠加 n）——整段回答是 JSON 字符串，真的敲回车会把 JSON 弄坏。
- 当回答落在一个具体的游戏上（推荐了某款折扣游戏、或报了某个游戏的价格与评分），把该游戏的官方商店链接
  填进 game_url、游戏名填进 game_name，页面上会用它渲染一张跳转卡片。只是泛泛回答、没有落到某一款游戏时，
  这两个字段给 null。

两个例子（只示范结构，实际回答请按上面的规矩自己组织）：

{"success": true, "game_name": "艾尔登法环", "game_url": "https://store.steampowered.com/app/1245620", "message": "《艾尔登法环》国区现价 ¥298，没有折扣，媒体评分 94。这个价格它是常态价，想等便宜可以看看本站折扣榜里有没有同类的。"}

{"success": true, "game_name": "星海远征", "game_url": "https://store.steampowered.com/app/900001", "message": "本站当前的折扣榜里，折扣最高的是《星海远征》，原价 ¥198 现价 ¥59.4，-70%，好评率 92%，属于「特别好评」。库里目前只有这些演示数据，跑过爬虫之后榜单会更全。"}
"""

# 单次请求的工具调用总次数上限。
# 这**不是**用来拦住用户第二次尝试的——用户想接着问，再发一条消息即可，配额按请求重置。
# 它只是一个成本上限：防止模型在两三个工具之间来回打转，把一次提问变成几十次外部请求。
#
# 6 这个数是照着 Java 侧的超时挑的：GameFinderServlet 读写超时都是 35 秒，而每个工具要
# 1~3 秒跑外部请求、每轮还要多一次大模型往返。次数放开的话，一次提问就会先在 Java 那边
# 超时，用户看到「目标网站响应较慢」，而这与实际的快慢无关。
MAX_TOOL_CALLS_PER_REQUEST = 6
# 大模型最多来回几轮。留够「查价 → 查口碑 → 汇总」这种多工具流程的余量，同时受上面同样的约束。
MAX_ROUNDS = 5

# 解析模型回复用的解码器。strict=False 允许字符串里出现真实换行等控制字符——模型写多段
# 回答时经常在 message 的字符串中间直接回车，标准 JSON 不接受，但这是可以安全容忍的。
_MODEL_JSON = json.JSONDecoder(strict=False)


def parse_model_reply(content: str):
    """
    把模型这一轮的回复解成 dict，解不出来返回 None。

    模型的输出不总是干净的：可能裹着 markdown 代码块、前后带解释文字、字符串里直接回车，
    甚至先吐一个对象再吐另一个。所以这里从每个 `{` 都试着解一次，用 raw_decode 停在第一个
    完整的对象上——这比「一个贪婪正则取出首尾大括号」宽容得多，那种取法一旦模型多写一段
    带大括号的解释就整个解不出来。
    """
    cleaned = re.sub(r"```(?:json)?", "", content).strip()
    for start, ch in enumerate(cleaned):
        if ch != "{":
            continue
        try:
            obj, _ = _MODEL_JSON.raw_decode(cleaned, start)
        except ValueError:
            continue
        if isinstance(obj, dict) and "message" in obj:
            return obj
    return None


def salvage_message(content: str) -> str:
    """
    JSON 整体解不出来时，把 message 里的那段话尽量捞出来。

    用户要看的是那段话，不是一串带引号的字段名。这里连「字符串没写完就被截断」也一并容忍：
    取到行尾、去掉可能残留的收尾引号与大括号，再把 \\n 之类的转义还原。
    """
    match = re.search(r'"message"\s*:\s*"(.*)', content, re.DOTALL)
    if not match:
        return ""
    text = re.sub(r'"\s*\}?\s*$', "", match.group(1))
    return text.replace("\\n", "\n").replace("\\t", "\t").replace('\\"', '"').strip()


@app.post("/api/v1/find-game")
async def find_game(query: GameQuery):
    user_query = query.user_query
    print(f"\n[Request Received] user_query: '{user_query}'")

    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": user_query}
    ]

    tool_calls_used = 0

    if client is None:
        return {
            "success": False,
            "game_name": None,
            "game_url": None,
            "message": "服务端没有配置大模型密钥，请按 README 在 game-finder/.env 里填好 DEEPSEEK_API_KEY 后重启服务。"
        }

    try:
        for _ in range(MAX_ROUNDS):
            response = client.chat.completions.create(
                model=MODEL_NAME,
                messages=messages,
                tools=tools,
                tool_choice="auto"
            )

            response_message = response.choices[0].message
            tool_calls = response_message.tool_calls

            # 若模型不需要再调用工具，直接整理出最终的文本并返回
            if not tool_calls:
                content = response_message.content or ""
                json_data = parse_model_reply(content)
                if json_data is not None:
                    return json_data
                # 解不出来时不要把整段 JSON 语法丢给用户——那段话本身通常是完整的
                print(f"[Warning] 模型这一轮的回复不是合法 JSON（长度 {len(content)}）：\n{content}\n---回复结束---")
                return {
                    "success": False,
                    "game_name": None,
                    "game_url": None,
                    "message": salvage_message(content) or content
                }

            # 否则，模型要求进行 Tool Call
            messages.append(response_message)

            for tool_call in tool_calls:
                function_name = tool_call.function.name
                function_args = json.loads(tool_call.function.arguments)

                if tool_calls_used >= MAX_TOOL_CALLS_PER_REQUEST:
                    print(f"[Warning] tool call budget exhausted ({MAX_TOOL_CALLS_PER_REQUEST}), refusing {function_name}")
                    res = {
                        "success": False,
                        "reason": f"本次提问的工具调用次数已达上限（{MAX_TOOL_CALLS_PER_REQUEST} 次）。"
                                  "请停止调用工具，用现有信息给出最终回答，并说明哪些还没查到。",
                    }
                else:
                    tool_calls_used += 1
                    if function_name == "search_game":
                        res = search_game(function_args.get("game_name", ""))
                    elif function_name == "browse_deals":
                        res = browse_deals(
                            function_args.get("category", "discount"),
                            function_args.get("limit", 8),
                        )
                    elif function_name == "search_reviews":
                        res = search_reviews(function_args.get("game_name", ""))
                    else:
                        res = {"success": False, "reason": f"Unknown tool name {function_name}"}

                messages.append({
                    "tool_call_id": tool_call.id,
                    "role": "tool",
                    "name": function_name,
                    "content": json.dumps(res, ensure_ascii=False),
                })

        raise HTTPException(status_code=500, detail="LLM reasoning loop timed out")

    except Exception as e:
        print(f"[Error] Failed to communicate with LLM or parse result: {e}")
        return {
            "success": False,
            "game_name": None,
            "game_url": None,
            "message": f"助手在查资料时出错了，请稍后重试。（错误原因: {str(e)}）"
        }

@app.get("/health")
def health():
    return {"status": "ok", "llm_connected": API_KEY is not None}

if __name__ == "__main__":
    import uvicorn
    # 键名与 Java 侧 app.properties 的 game-finder.port / 环境变量 GAME_FINDER_PORT 保持一致，
    # 这样两边看的是同一个配置名，不会各改各的；PORT 作为旧键名保留兼容
    port = int(os.getenv("GAME_FINDER_PORT", os.getenv("PORT", 8090)))
    # 在生产/测试运行中，关闭 reload=True 选项以防 stdout buffering 问题
    uvicorn.run("main:app", host="127.0.0.1", port=port, reload=False)