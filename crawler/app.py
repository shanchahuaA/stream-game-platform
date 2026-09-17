import os
import re
import requests
from bs4 import BeautifulSoup
from dotenv import load_dotenv
from fastapi import FastAPI, Query

import sys
import time
import random

load_dotenv()

# 访问 Steam / Epic 时走的 HTTP 代理。留空表示直连，由 crawler/.env 里的 CRAWLER_PROXY 配置。
DEFAULT_PROXY = os.getenv("CRAWLER_PROXY", "")

sys.stdout.reconfigure(encoding='utf-8')
app = FastAPI(title="Steam Specials API")


def steam_discount(start: int, limit: int, proxy: str) -> list[dict]:
    games = []

    count = limit

    proxies = {"http": proxy, "https": proxy} if proxy else None
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept-Language": "zh-CN,zh;q=0.9",
        "X-Requested-With": "XMLHttpRequest"
    }

    url = f"https://store.steampowered.com/search/results?query&start={start}&count={count}&specials=1&cc=cn&infinite=1"

    time.sleep(random.uniform(1, 2))
    try:
        response = requests.get(url, headers=headers, proxies=proxies, timeout=15)
        if response.status_code != 200:
            return games

        data = response.json()
        html_content = data.get("results_html", "")
        if not html_content.strip():
            return games

        soup = BeautifulSoup(html_content, "html.parser")
        rows = soup.select("a.search_result_row")
        if not rows:
            return games

        discount_rank = start
        for row in rows:
            if len(games) >= limit:
                break
            discount_rank += 1
            game_id = row.get("data-ds-appid")
            title = row.select_one("span.title").text
            img = row.select_one("img")
            cover_url = img.get("src") if img else None
            shop_url = row.get("href")

            div_original_price = row.select_one("div.discount_original_price")
            div_final_price = row.select_one("div.discount_final_price")
            div_discount_pct = row.select_one("div.discount_pct")

            raw_original_price = div_original_price.text if div_original_price else (div_final_price.text if div_final_price else "0")
            raw_final_price = div_final_price.text if div_final_price else "0"
            raw_discount_percent = div_discount_pct.text if div_discount_pct else "0"

            clean_strO = raw_original_price.replace(",", "")
            matchO = re.search(r"\d+(\.\d+)?", clean_strO)
            original_price = float(matchO.group(0)) if matchO else 0.0

            clean_strF = raw_final_price.replace(",", "")
            matchF = re.search(r"\d+(\.\d+)?", clean_strF)
            final_price = float(matchF.group(0)) if matchF else 0.0

            matchD = re.search(r"\d+", raw_discount_percent)
            discount_percent = int(matchD.group(0)) if matchD else 0

            review_tier = None
            positive_rate = 0.0
            review_count = 0

            review = row.select_one("span.search_review_summary")
            raw_tooltip = review.get("data-tooltip-html") if review else None

            if raw_tooltip:
                parts = raw_tooltip.split("<br>")
                review_tier = parts[0]
                review_text = parts[1]

                rate_match = re.search(r"(\d+)%", review_text)
                if rate_match:
                    positive_rate = float(rate_match.group(1))
                else:
                    positive_rate = 0.0

                count_match = re.search(r"([\d,]+)\s*篇", review_text)
                if count_match:
                    raw_count = count_match.group(1)
                    clean_count = raw_count.replace(",", "")
                    review_count = int(clean_count)

            game_data = {
                "platform": "steam",
                "platform_game_id": game_id,
                "gname": title,
                "cover_url": cover_url,
                "shop_url": shop_url,
                "original_price": original_price,
                "final_price": final_price,
                "discount_percent": discount_percent,
                "top_seller_rank": None,
                "discount_rank": discount_rank,
                "review_count": review_count,
                "positive_rate": positive_rate,
                "review_tier": review_tier
            }
            games.append(game_data)



    except Exception as e:
        print(f"数据处理发生错误: {e}")


    return games

def steam_topseller(start: int, limit: int, proxy: str) -> list[dict]:
    games = []

    count = limit

    proxies = {"http": proxy, "https": proxy} if proxy else None
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept-Language": "zh-CN,zh;q=0.9",
        "X-Requested-With": "XMLHttpRequest"
    }

    url = f"https://store.steampowered.com/search/results?query&start={start}&count={limit}&filter=topsellers&cc=cn&infinite=1"

    time.sleep(random.uniform(1, 2))
    try:
        response = requests.get(url, headers=headers, proxies=proxies, timeout=15)
        if response.status_code != 200:
            return games

        data = response.json()
        html_content = data.get("results_html", "")
        if not html_content.strip():
            return games

        soup = BeautifulSoup(html_content, "html.parser")
        rows = soup.select("a.search_result_row")
        if not rows:
            return games

        top_seller_rank = start
        for row in rows:
            if len(games) >= limit:
                break
            top_seller_rank += 1
            game_id = row.get("data-ds-appid")
            title = row.select_one("span.title").text
            img = row.select_one("img")
            cover_url = img.get("src") if img else None
            shop_url = row.get("href")

            div_original_price = row.select_one("div.discount_original_price")
            div_final_price = row.select_one("div.discount_final_price")
            div_discount_pct = row.select_one("div.discount_pct")

            raw_original_price = div_original_price.text if div_original_price else (div_final_price.text if div_final_price else "0")
            raw_final_price = div_final_price.text if div_final_price else "0"
            raw_discount_percent = div_discount_pct.text if div_discount_pct else "0"

            clean_strO = raw_original_price.replace(",", "")
            matchO = re.search(r"\d+(\.\d+)?", clean_strO)
            original_price = float(matchO.group(0)) if matchO else 0.0

            clean_strF = raw_final_price.replace(",", "")
            matchF = re.search(r"\d+(\.\d+)?", clean_strF)
            final_price = float(matchF.group(0)) if matchF else 0.0

            matchD = re.search(r"\d+", raw_discount_percent)
            discount_percent = int(matchD.group(0)) if matchD else 0

            review_tier = None
            positive_rate = 0.0
            review_count = 0

            review = row.select_one("span.search_review_summary")
            raw_tooltip = review.get("data-tooltip-html") if review else None

            if raw_tooltip:
                parts = raw_tooltip.split("<br>")
                review_tier = parts[0]
                review_text = parts[1]

                rate_match = re.search(r"(\d+)%", review_text)
                if rate_match:
                    positive_rate = float(rate_match.group(1))
                else:
                    positive_rate = 0.0

                count_match = re.search(r"([\d,]+)\s*篇", review_text)
                if count_match:
                    raw_count = count_match.group(1)
                    clean_count = raw_count.replace(",", "")
                    review_count = int(clean_count)

            game_data = {
                "platform": "steam",
                "platform_game_id": game_id,
                "gname": title,
                "cover_url": cover_url,
                "shop_url": shop_url,
                "original_price": original_price,
                "final_price": final_price,
                "discount_percent": discount_percent,
                "top_seller_rank": top_seller_rank,
                "discount_rank": None,
                "review_count": review_count,
                "positive_rate": positive_rate,
                "review_tier": review_tier
            }
            games.append(game_data)



    except Exception as e:
        print(f"数据处理发生错误: {e}")


    return games

def epic_freegames(proxy: str) -> list[dict]:
    games = []
    proxies = {"http": proxy, "https": proxy} if proxy else None
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept-Language": "zh-CN,zh;q=0.9",
    }
    url = "https://store-site-backend-static.ak.epicgames.com/freeGamesPromotions?locale=zh-CN&country=CN&allowCountries=CN"

    try:
        response = requests.get(url, headers=headers, proxies=proxies, timeout=15)
        if response.status_code != 200:
            return games

        data = response.json()
        elements = data.get("data", {}).get("Catalog", {}).get("searchStore", {}).get("elements", [])

        for el in elements:
            promotions = el.get("promotions")
            if not promotions:
                continue

            price_info = el.get("price", {}).get("totalPrice", {})
            original_price = (price_info.get("originalPrice", 0)) / 100.0
            discount_price = (price_info.get("discountPrice", 0)) / 100.0

            review_tier = None
            final_price = original_price
            discount_percent = 0


            is_currently_free = False
            promotional_offers = promotions.get("promotionalOffers", [])
            if promotional_offers:
                for offer_group in promotional_offers:
                    for promo in offer_group.get("promotionalOffers", []):
                        discount_pct = promo.get("discountSetting", {}).get("discountPercentage", 100)
                        if discount_pct == 0:
                            is_currently_free = True

            if is_currently_free and discount_price == 0:
                review_tier = "限时免费"
                final_price = 0.0
                discount_percent = 100
            else:
                is_upcoming_free = False
                upcoming_offers = promotions.get("upcomingPromotionalOffers", [])
                if upcoming_offers:
                    for offer_group in upcoming_offers:
                        for promo in offer_group.get("promotionalOffers", []):
                            discount_pct = promo.get("discountSetting", {}).get("discountPercentage", 100)
                            if discount_pct == 0:
                                is_upcoming_free = True

                if is_upcoming_free:
                    review_tier = "即将免费"
                    final_price = original_price
                    discount_percent = 0

            if not review_tier:
                continue

            cover_url = None
            key_images = el.get("keyImages", [])
            for img in key_images:
                if img.get("type") in ["Thumbnail", "OfferImageWide", "DieselStoreFrontWide"]:
                    cover_url = img.get("url")
                    break
            if not cover_url and key_images:
                cover_url = key_images[0].get("url")
            slug = el.get("productSlug") or el.get("urlSlug")
            shop_url = f"https://store.epicgames.com/zh-CN/p/{slug}" if slug else "https://store.epicgames.com/zh-CN/free-games"

            game_data = {
                "platform": "epic",
                "platform_game_id": el.get("id"),
                "gname": el.get("title"),
                "cover_url": cover_url,
                "shop_url": shop_url,
                "original_price": original_price,
                "final_price": final_price,
                "discount_percent": discount_percent,
                "top_seller_rank": None,
                "discount_rank": None,
                "review_count": 0,
                "positive_rate": 0.0,
                "review_tier": review_tier
            }
            games.append(game_data)

    except Exception as e:
        print(f"Epic数据处理发生错误: {e}")

    return games


# 定义 API 接口
@app.get("/steam/discount")
def get_discount(
        start: int = Query(default=0, ge=0, description="分页起始偏移量"),
        limit: int = Query(default=50, ge=1, le=500, description="限制获取数量"),
        proxy: str = Query(default=DEFAULT_PROXY, description="代理服务器，留空表示直连")
):

    game_list = steam_discount(start=start, limit=limit, proxy=proxy)
    return {
        "success": True,
        "total": len(game_list),
        "data": game_list
    }

@app.get("/steam/topsellers")
def get_topsellers(
        start: int = Query(default=0, ge=0, description="分页起始偏移量"),
        limit: int = Query(default=50, ge=1, le=500, description="限制获取数量"),
        proxy: str = Query(default=DEFAULT_PROXY, description="代理服务器，留空表示直连")
):

    game_list = steam_topseller(start=start, limit=limit, proxy=proxy)
    return {
        "success": True,
        "total": len(game_list),
        "data": game_list
    }


@app.get("/epic/freegames")
def get_epic_freegames(
        proxy: str = Query(default=DEFAULT_PROXY, description="代理服务器，留空表示直连")
):
    game_list = epic_freegames(proxy=proxy)
    return {
        "success": True,
        "total": len(game_list),
        "data": game_list
    }


@app.get("/health")
def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn

    # 单独运行 python app.py 时使用；Java 侧启动时由 PythonManager 传入同一个端口
    uvicorn.run("app:app", host="127.0.0.1", port=int(os.getenv("CRAWLER_PORT", 8099)), reload=False)