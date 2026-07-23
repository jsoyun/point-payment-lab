const state = { products: [], logs: [] };
const $ = (selector) => document.querySelector(selector);
const escapeHtml = (value = '') => String(value).replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
const formatPoint = (value) => Number(value || 0).toLocaleString('ko-KR');
const formatDate = (value) => value ? new Date(value).toLocaleString('ko-KR') : '—';

function toast(message) {
  const el = $('#toast'); el.textContent = message; el.classList.add('show');
  clearTimeout(toast.timer); toast.timer = setTimeout(() => el.classList.remove('show'), 2400);
}

async function api(method, url, body) {
  const startedAt = new Date();
  let response, data;
  try {
    response = await fetch(url, { method, headers: body ? {'Content-Type':'application/json'} : {}, body: body ? JSON.stringify(body) : undefined });
    const text = await response.text(); data = text ? JSON.parse(text) : null;
    addLog({method, url, body, status: response.status, ok: response.ok, data, startedAt});
    if (!response.ok) throw new Error(data?.message || `HTTP ${response.status}`);
    return data;
  } catch (error) {
    if (!response) addLog({method, url, body, status:'NETWORK', ok:false, data:{message:error.message}, startedAt});
    toast(`요청 실패: ${error.message}`); throw error;
  }
}

function addLog(log) {
  state.logs.unshift(log); $('#log-count').textContent = state.logs.length; renderLogs();
}

function renderLogs() {
  const root = $('#api-logs');
  if (!state.logs.length) { root.innerHTML = '<div class="empty">아직 API 호출이 없습니다.</div>'; return; }
  root.innerHTML = state.logs.map(log => `<article class="log"><div class="log-head"><span class="method ${log.method}">${log.method}</span><code>${escapeHtml(log.url)}</code><time>${log.startedAt.toLocaleTimeString('ko-KR')}</time><span class="${log.ok?'http-ok':'http-error'}">${log.status}</span></div><pre>${escapeHtml(JSON.stringify({request:log.body ?? null,response:log.data}, null, 2))}</pre></article>`).join('');
}

function downloadLogs() {
  if (!state.logs.length) return toast('다운로드할 API 로그가 없습니다.');
  const exportedAt = new Date();
  const exportData = {
    metadata: {
      application: 'Point Payment Lab',
      exportedAt: exportedAt.toISOString(),
      browserTimeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      apiBaseUrl: window.location.origin,
      logCount: state.logs.length
    },
    logs: state.logs.map((log, index) => ({
      sequence: state.logs.length - index,
      requestedAt: log.startedAt.toISOString(),
      method: log.method,
      url: log.url,
      status: log.status,
      success: log.ok,
      request: log.body ?? null,
      response: log.data ?? null
    }))
  };
  const blob = new Blob([JSON.stringify(exportData, null, 2)], {type:'application/json;charset=utf-8'});
  const link = document.createElement('a');
  const timestamp = exportedAt.toISOString().replace(/[:.]/g, '-');
  link.href = URL.createObjectURL(blob);
  link.download = `point-payment-lab-api-log-${timestamp}.json`;
  document.body.appendChild(link);
  link.click();
  URL.revokeObjectURL(link.href);
  link.remove();
  toast(`${state.logs.length}건의 API 로그를 다운로드했습니다.`);
}

async function loadProducts() {
  state.products = await api('GET','/api/admin/voucher-products');
  $('#shop-products').innerHTML = state.products.length ? state.products.map(p => `<article class="product-card"><div class="product-code">${escapeHtml(p.voucherProductCode)}</div><h3>${escapeHtml(p.voucherName)}</h3><div class="price">${formatPoint(p.sellPrice)} P</div><div class="product-meta"><span>${p.useTerm}일 사용</span><button onclick="buyProduct(${p.id})">포인트 구매</button></div></article>`).join('') : '<div class="empty">등록된 상품이 없습니다.</div>';
  $('#admin-products').innerHTML = state.products.map(p => `<tr><td>${p.id}</td><td><code>${escapeHtml(p.voucherProductCode)}</code></td><td>${escapeHtml(p.voucherName)}</td><td>${formatPoint(p.sellPrice)} P</td><td>${p.useTerm}일</td></tr>`).join('') || '<tr><td colspan="5">등록된 상품이 없습니다.</td></tr>';
}

async function loadWallet() {
  const uid = $('#wallet-uid').value.trim(); if (!uid) return toast('지갑 UID를 입력하세요.');
  const data = await api('GET',`/api/point-wallets/${encodeURIComponent(uid)}/summary`);
  $('#wallet-balance').textContent = formatPoint(data.totalBalance);
  $('#wallet-detail').innerHTML = `wallet id: ${data.pointWalletId}<br>${data.balances.map(b => `point_balance #${b.pointBalanceId}: ${formatPoint(b.balance)} P`).join('<br>')}`;
  return data;
}

window.buyProduct = async function(productId) {
  const product = state.products.find(p => p.id === productId); if (!product) return;
  let wallet;
  try { wallet = await loadWallet(); } catch { return; }
  const pointBalanceId = wallet.balances[0]?.pointBalanceId;
  if (!pointBalanceId) return toast('사용할 point_balance가 없습니다.');
  const orderId = `ORDER-${Date.now()}`;
  try {
    const result = await api('POST','/api/payments/point/legacy',{orderId,pointWalletUid:wallet.pointWalletUid,voucherProductId:product.id,pointBalanceId,point:product.sellPrice});
    toast(`${product.voucherName} 발급 완료`); await Promise.all([loadWallet(),loadPurchases(),loadProvider()]);
    document.querySelector('[data-tab="shop"]').click();
  } catch (_) {}
};

async function loadPurchases() {
  const orderId = $('#purchase-order-filter').value.trim();
  const list = await api('GET',`/api/voucher-purchases${orderId ? `?orderId=${encodeURIComponent(orderId)}` : ''}`);
  $('#purchases').innerHTML = list.length ? list.map(v => `<article class="voucher"><div><small>주문 번호</small><b>${escapeHtml(v.orderId)}</b></div><div><small>바우처 / PIN</small><code>${escapeHtml(v.voucherNumber)}</code><br><code>${escapeHtml(v.pinNumber)}</code></div><div><small>유효기간</small>${formatDate(v.validUntil)}<br><span class="status ${v.issueStatus}">${v.issueStatus}</span></div><button class="refund" ${v.issueStatus==='CANCELED'?'disabled':''} onclick="refundVoucher('${escapeHtml(v.voucherNumber)}')">환불 요청</button></article>`).join('') : '<div class="empty">구매 내역이 없습니다.</div>';
}

window.refundVoucher = async function(voucherNumber) {
  try { await api('POST','/api/refunds/point/legacy',{voucherNumber}); toast('포인트 환불이 완료되었습니다.'); await Promise.all([loadWallet(),loadPurchases(),loadProvider()]); } catch (_) {}
};

async function loadProvider() {
  const orderId = $('#provider-order-filter').value.trim();
  const data = await api('GET',`/mock/voucher-provider/vouchers${orderId ? `?orderId=${encodeURIComponent(orderId)}` : ''}`);
  $('#calls-total').textContent=data.callCount.total; $('#calls-issue').textContent=data.callCount.issue; $('#calls-cancel').textContent=data.callCount.cancel;
  $('#provider-vouchers').innerHTML = data.vouchers.map(v => `<tr><td>${v.id}</td><td>${escapeHtml(v.orderId)}</td><td><code>${escapeHtml(v.voucherProductCode)}</code></td><td><code>${escapeHtml(v.voucherNumber)}</code></td><td><span class="status ${v.status}">${v.status}</span></td><td>${formatDate(v.createdAt)}</td></tr>`).join('') || '<tr><td colspan="6">발행 내역이 없습니다.</td></tr>';
}

document.querySelectorAll('.tab').forEach(tab => tab.addEventListener('click', () => {
  document.querySelectorAll('.tab,.panel').forEach(el => el.classList.remove('active'));
  tab.classList.add('active'); $(`#${tab.dataset.tab}`).classList.add('active');
  if (tab.dataset.tab==='provider') loadProvider().catch(()=>{});
}));

$('#load-wallet').onclick=()=>loadWallet().catch(()=>{});
$('#load-purchases').onclick=()=>loadPurchases().catch(()=>{});
$('#load-provider').onclick=()=>loadProvider().catch(()=>{});
$('#load-admin-products').onclick=()=>loadProducts().catch(()=>{});
$('#refresh-shop').onclick=()=>Promise.all([loadWallet(),loadProducts(),loadPurchases()]).catch(()=>{});
$('#clear-logs').onclick=()=>{state.logs=[];$('#log-count').textContent='0';renderLogs();};
$('#download-logs').onclick=downloadLogs;
$('#product-form').addEventListener('submit', async event => {
  event.preventDefault(); const form = new FormData(event.currentTarget);
  const body={voucherProductCode:form.get('voucherProductCode'),voucherName:form.get('voucherName'),sellPrice:Number(form.get('sellPrice')),useTerm:Number(form.get('useTerm'))};
  try { const result=await api('POST','/api/admin/voucher-products',body); $('#admin-response').textContent=JSON.stringify({request:body,response:result},null,2); toast('상품이 등록되었습니다.'); await loadProducts(); } catch(error) { $('#admin-response').textContent=JSON.stringify({request:body,error:error.message},null,2); }
});

Promise.all([loadWallet(),loadProducts(),loadPurchases()]).catch(()=>{});
