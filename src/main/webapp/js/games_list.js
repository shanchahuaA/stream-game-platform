// ── 基础配置 ──────────────────────────────────────────────────────
let currentPage = 1;
const FRONTEND_PAGE_SIZE = 12;   // 前端每页 12 条（3行×4列）
const BACKEND_PAGE_SIZE  = 200;  // 后端单次拉取全量上限
let totalPages = 1;

// 原始数据与处理后数据缓存
let rawGamesList       = [];
let processedGamesList = [];

// 用户登录状态
let currentUser = null;

// ── DOM 元素获取 ──────────────────────────────────────────────────
const gameListBody    = document.getElementById('gameListBody');
const searchInput     = document.getElementById('searchInput');
const searchBtn       = document.getElementById('searchBtn');
const resetBtn        = document.getElementById('resetBtn');
const priceLimitInput = document.getElementById('priceLimitInput');
const sortSelect      = document.getElementById('sortSelect');
const listTypeRadios  = document.getElementsByName('listType');

// ── IntersectionObserver 卡片入场动画 ────────────────────────────
let cardObserver = null;

function setupCardAnimations() {
    if (cardObserver) {
        cardObserver.disconnect();
    }
    // prefers-reduced-motion 降级：直接显示
    const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReduced) {
        document.querySelectorAll('.game-card').forEach(card => {
            card.style.opacity = '1';
            card.style.transform = 'none';
        });
        return;
    }

    cardObserver = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('is-visible');
                cardObserver.unobserve(entry.target);
            }
        });
    }, { threshold: 0.1, rootMargin: '0px 0px -40px 0px' });

    document.querySelectorAll('.game-card').forEach((card, i) => {
        card.style.animationDelay = (i % FRONTEND_PAGE_SIZE * 0.04) + 's';
        cardObserver.observe(card);
    });
}

// ── 初始化事件监听 ────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    // 检查登录状态
    const userStr = localStorage.getItem('currentUser');
    if (userStr) {
        try { currentUser = JSON.parse(userStr); } catch (e) { currentUser = null; }
    }

    loadData();

    // 榜单切换事件
    listTypeRadios.forEach(radio => {
        radio.addEventListener('change', () => {
            currentPage = 1;
            loadData();
        });
    });

    // 搜索按钮
    searchBtn.addEventListener('click', () => {
        currentPage = 1;
        processAndRender();
    });

    // 搜索回车
    searchInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            currentPage = 1;
            processAndRender();
        }
    });

    // 重置
    resetBtn.addEventListener('click', () => {
        searchInput.value = '';
        priceLimitInput.value = '';
        sortSelect.value = 'default';
        currentPage = 1;
        processAndRender();
    });

    // 价格过滤
    priceLimitInput.addEventListener('input', () => {
        currentPage = 1;
        processAndRender();
    });

    // 排序规则
    sortSelect.addEventListener('change', () => {
        currentPage = 1;
        processAndRender();
    });
});

// ── 获取当前选中的榜单类型 ─────────────────────────────────────────
function getSelectedListType() {
    let selectedType = 'all';
    listTypeRadios.forEach(radio => {
        if (radio.checked) selectedType = radio.value;
    });
    return selectedType;
}

// ── 加载数据 ──────────────────────────────────────────────────────
function loadData() {
    // 显示加载中
    gameListBody.innerHTML = `
        <div class="loading-state">
            <div class="spinner" style="width:32px;height:32px;border-width:3px;"></div>
            <span>数据加载中…</span>
        </div>`;

    const type = getSelectedListType();
    let action = '';

    if (type === 'giveaway') {
        action = 'getGiveawayGames';
    } else if (type === 'f2p' || type === 'all') {
        action = 'getGamesByPage';
    } else {
        action = type === 'topseller' ? 'getAllByTopSeller' : 'getAllByDiscount';
    }

    const url = `games?action=${action}&pageNum=1&pageSize=${BACKEND_PAGE_SIZE}`;

    fetch(url)
        .then(response => {
            if (!response.ok) throw new Error('网络响应异常');
            return response.json();
        })
        .then(res => {
            if (res.code === 200) {
                rawGamesList = res.data.list || [];
                processAndRender();
            } else {
                showError(res.message || '获取数据失败');
            }
        })
        .catch(error => {
            console.error('Fetch Error:', error);
            showError('请求后端接口失败，请检查服务是否启动或路径是否正确');
        });
}

// ── 前端处理数据并触发渲染 ────────────────────────────────────────
function processAndRender() {
    const type     = getSelectedListType();
    const query    = searchInput.value.trim().toLowerCase();
    const maxPrice = parseFloat(priceLimitInput.value);
    const sortRule = sortSelect.value;

    // 数据过滤
    processedGamesList = rawGamesList.filter(game => {
        if (query && (!game.gname || !game.gname.toLowerCase().includes(query))) return false;
        if (type === 'f2p' && game.finalPrice !== 0) return false;
        if (!isNaN(maxPrice) && game.finalPrice > maxPrice) return false;
        return true;
    });

    // 排序
    applySort(processedGamesList, sortRule);

    // 分页
    totalPages = Math.ceil(processedGamesList.length / FRONTEND_PAGE_SIZE) || 1;
    if (currentPage > totalPages) currentPage = totalPages;

    const startIdx  = (currentPage - 1) * FRONTEND_PAGE_SIZE;
    const pageSlice = processedGamesList.slice(startIdx, startIdx + FRONTEND_PAGE_SIZE);

    renderTable(pageSlice);
    updatePagination(currentPage, totalPages);
}

// ── 排序 ──────────────────────────────────────────────────────────
function applySort(games, rule) {
    if (rule === 'default') return;
    const getRankWeight = (rank) => (rank === 0 || rank === null || rank === undefined) ? 999999 : rank;

    games.sort((a, b) => {
        if (rule === 'discountDesc' || rule === 'discountAsc') {
            const discA = a.discountPercent || 0;
            const discB = b.discountPercent || 0;
            if (discA !== discB) return rule === 'discountDesc' ? (discB - discA) : (discA - discB);
            const rankA = getRankWeight(a.topSellerRank);
            const rankB = getRankWeight(b.topSellerRank);
            if (rankA !== rankB) return rankA - rankB;
            return (b.reviewCount || 0) - (a.reviewCount || 0);
        }
        if (rule === 'priceAsc' || rule === 'priceDesc') {
            const priceA = a.finalPrice || 0;
            const priceB = b.finalPrice || 0;
            if (priceA !== priceB) return rule === 'priceAsc' ? (priceA - priceB) : (priceB - priceA);
            const discRankA = getRankWeight(a.discountRank);
            const discRankB = getRankWeight(b.discountRank);
            if (discRankA !== discRankB) return discRankA - discRankB;
            const rankA = getRankWeight(a.topSellerRank);
            const rankB = getRankWeight(b.topSellerRank);
            if (rankA !== rankB) return rankA - rankB;
            return (b.reviewCount || 0) - (a.reviewCount || 0);
        }
        if (rule === 'rateDesc') {
            const rateA = a.positiveRate || 0.0;
            const rateB = b.positiveRate || 0.0;
            if (rateA !== rateB) return rateB - rateA;
            return (b.reviewCount || 0) - (a.reviewCount || 0);
        }
        if (rule === 'countDesc') {
            return (b.reviewCount || 0) - (a.reviewCount || 0);
        }
        return 0;
    });
}

// ── 渲染卡片 ──────────────────────────────────────────────────────
function renderTable(games) {
    if (games.length === 0) {
        gameListBody.innerHTML = `
            <div class="empty-state" style="grid-column:1/-1;">
                <span class="empty-icon">🔍</span>
                <h3>没有找到匹配的游戏</h3>
                <p>尝试换个关键词或调整筛选条件</p>
            </div>`;
        return;
    }

    // 用 DocumentFragment 批量插入
    const fragment = document.createDocumentFragment();

    games.forEach(game => {
        const card = document.createElement('div');
        card.className = 'game-card';
        card.setAttribute('role', 'article');

        // 封面
        const cover = document.createElement('div');
        cover.className = 'game-card-cover';

        if (game.coverUrl) {
            const img = document.createElement('img');
            img.src     = game.coverUrl;
            img.alt     = game.gname ? `${game.gname} 封面` : '游戏封面';
            img.loading = 'lazy';
            cover.appendChild(img);
        }

        // 折扣徽章
        if (game.discountPercent > 0) {
            const db = document.createElement('div');
            db.className = 'discount-badge';
            db.textContent = `-${game.discountPercent}%`;
            db.setAttribute('aria-label', `折扣 ${game.discountPercent}%`);
            cover.appendChild(db);
        }

        // 平台徽章
        if (game.platform) {
            const pb = document.createElement('div');
            pb.className = 'platform-badge';
            pb.textContent = game.platform;
            cover.appendChild(pb);
        }

        card.appendChild(cover);

        // 卡片正文
        const body = document.createElement('div');
        body.className = 'game-card-body';

        // 标题
        const title = document.createElement('h3');
        title.className = 'game-card-title';
        title.textContent = game.gname || '未知名称';
        body.appendChild(title);

        // Meta 区
        const meta = document.createElement('div');
        meta.className = 'game-card-meta';

        const priceInfo = document.createElement('div');
        priceInfo.className = 'price-info';

        const origPrice = document.createElement('span');
        origPrice.className = 'original-price tabular-nums';
        origPrice.textContent = (game.originalPrice === 0 || game.originalPrice === null)
            ? '免费/未公布' : `¥${game.originalPrice}`;
        priceInfo.appendChild(origPrice);

        const finalPriceEl = document.createElement('span');
        finalPriceEl.className = 'final-price tabular-nums';
        finalPriceEl.textContent = game.finalPrice === 0 ? '免费' : `¥${game.finalPrice}`;
        priceInfo.appendChild(finalPriceEl);
        meta.appendChild(priceInfo);

        const reviewInfo = document.createElement('div');
        reviewInfo.className = 'review-info';

        const reviewCount = document.createElement('span');
        reviewCount.className = 'review-count tabular-nums';
        reviewCount.textContent = `${game.reviewCount || 0} 条评价`;
        reviewInfo.appendChild(reviewCount);

        const positiveRate = document.createElement('span');
        positiveRate.className = 'positive-rate tabular-nums';
        positiveRate.textContent = game.positiveRate ? `${game.positiveRate}%` : '暂无';
        reviewInfo.appendChild(positiveRate);

        const reviewTierEl = document.createElement('span');
        reviewTierEl.className = 'review-tier';
        reviewTierEl.textContent = game.reviewTier || '暂无评价';
        reviewInfo.appendChild(reviewTierEl);
        meta.appendChild(reviewInfo);

        body.appendChild(meta);

        // 操作区
        const actions = document.createElement('div');
        actions.className = 'game-card-actions';

        const shopLink = document.createElement('a');
        shopLink.href    = game.shopUrl || '#';
        shopLink.target  = '_blank';
        shopLink.rel     = 'noopener noreferrer';
        shopLink.className = 'gc-btn-primary';
        shopLink.textContent = '购买链接';
        shopLink.setAttribute('aria-label', `购买 ${game.gname || '游戏'}`);
        actions.appendChild(shopLink);

        if (!currentUser) {
            const loginBtn = document.createElement('a');
            loginBtn.href        = 'login.html';
            loginBtn.className   = 'gc-btn-login';
            loginBtn.textContent = '登录收藏';
            actions.appendChild(loginBtn);
        } else {
            const favBtn = document.createElement('button');
            favBtn.className = 'gc-btn-fav';
            favBtn.textContent = '⭐ 收藏';
            favBtn.setAttribute('aria-label', `收藏 ${game.gname || '游戏'}`);
            favBtn.addEventListener('click', () => addFavorite(game.id, favBtn));
            actions.appendChild(favBtn);
        }

        body.appendChild(actions);
        card.appendChild(body);
        fragment.appendChild(card);
    });

    gameListBody.innerHTML = '';
    gameListBody.appendChild(fragment);

    // 触发入场动画
    setupCardAnimations();
}

// ── 分页渲染 ─────────────────────────────────────────────────────
function updatePagination(pageNum, pages) {
    currentPage = pageNum || 1;
    totalPages  = pages  || 1;

    const container = document.getElementById('paginationContainer');
    if (!container) return;
    container.innerHTML = '';

    // 上一页
    const prevBtn = document.createElement('button');
    prevBtn.textContent = '← 上一页';
    prevBtn.className   = 'page-wide-btn';
    prevBtn.disabled    = currentPage <= 1;
    prevBtn.setAttribute('aria-label', '上一页');
    prevBtn.addEventListener('click', () => { if (currentPage > 1) { currentPage--; processAndRender(); } });
    container.appendChild(prevBtn);

    // 页码项
    const pageItems = [];
    if (totalPages <= 9) {
        for (let i = 1; i <= totalPages; i++) pageItems.push(i);
    } else {
        if (currentPage <= 5) {
            for (let i = 1; i <= 7; i++) pageItems.push(i);
            pageItems.push('...'); pageItems.push(totalPages);
        } else if (currentPage >= totalPages - 4) {
            pageItems.push(1); pageItems.push('...');
            for (let i = totalPages - 6; i <= totalPages; i++) pageItems.push(i);
        } else {
            pageItems.push(1); pageItems.push('...');
            for (let i = currentPage - 2; i <= currentPage + 2; i++) pageItems.push(i);
            pageItems.push('...'); pageItems.push(totalPages);
        }
    }

    pageItems.forEach(item => {
        if (item === '...') {
            const span = document.createElement('span');
            span.className   = 'page-ellipsis';
            span.textContent = '…';
            container.appendChild(span);
        } else {
            const btn = document.createElement('button');
            btn.textContent = item;
            btn.className   = 'page-btn' + (item === currentPage ? ' active' : '');
            btn.setAttribute('aria-label', `第 ${item} 页`);
            if (item === currentPage) btn.setAttribute('aria-current', 'page');
            btn.addEventListener('click', () => { currentPage = item; processAndRender(); });
            container.appendChild(btn);
        }
    });

    // 下一页
    const nextBtn = document.createElement('button');
    nextBtn.textContent = '下一页 →';
    nextBtn.className   = 'page-wide-btn';
    nextBtn.disabled    = currentPage >= totalPages;
    nextBtn.setAttribute('aria-label', '下一页');
    nextBtn.addEventListener('click', () => { if (currentPage < totalPages) { currentPage++; processAndRender(); } });
    container.appendChild(nextBtn);
}

// ── 错误显示 ─────────────────────────────────────────────────────
function showError(msg) {
    const errDiv = document.createElement('div');
    errDiv.className = 'error-state';
    errDiv.textContent = '错误: ' + msg;
    gameListBody.innerHTML = '';
    gameListBody.appendChild(errDiv);
    const container = document.getElementById('paginationContainer');
    if (container) container.innerHTML = '';
}

// ── 添加收藏 ─────────────────────────────────────────────────────
function addFavorite(gameId, btn) {
    if (!currentUser) { alert('请先登录'); return; }

    btn.disabled     = true;
    btn.textContent  = '收藏中…';

    fetch(`userFavorite?action=add&userId=${currentUser.id}&gameId=${gameId}`)
        .then(response => response.json())
        .then(result => {
            if (result.status === 'success') {
                btn.textContent = '已收藏 ✓';
                btn.style.opacity = '0.6';
                btn.style.cursor  = 'default';
            } else {
                alert(result.message || '收藏失败');
                btn.disabled    = false;
                btn.textContent = '⭐ 收藏';
            }
        })
        .catch(error => {
            console.error(error);
            alert('网络错误，请稍后重试');
            btn.disabled    = false;
            btn.textContent = '⭐ 收藏';
        });
}
