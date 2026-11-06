(function () {
  'use strict'

  const store = window.TravelExpenseStore
  const excel = window.TravelExpenseExcel
  const view = document.getElementById('view')
  const pageTitle = document.getElementById('pageTitle')
  const backButton = document.getElementById('backButton')
  const topAction = document.getElementById('topAction')
  const tabbar = document.getElementById('tabbar')
  const toastElement = document.getElementById('toast')
  const modal = document.getElementById('modal')
  const modalSheet = document.getElementById('modalSheet')

  const TOOL_VERSION = '1.0.0'
  const TOP_LEVEL = new Set(['trips', 'stats', 'settings'])
  const routeStack = [{ name: 'trips' }]
  let selectedCategoryId = ''
  let toastTimer = 0
  let tripFormDates = null

  function currentRoute() {
    return routeStack[routeStack.length - 1]
  }

  function navigate(name, params) {
    routeStack.push(Object.assign({ name: name }, params || {}))
    render()
  }

  function replaceRoute(name, params) {
    routeStack[routeStack.length - 1] = Object.assign({ name: name }, params || {})
    render()
  }

  function resetRoute(name) {
    routeStack.splice(0, routeStack.length, { name: name })
    render()
  }

  function goBack() {
    if (routeStack.length > 1) {
      routeStack.pop()
      render()
      return true
    }
    return false
  }

  window.toolboxNavigateBack = function () {
    if (!modal.hidden) {
      closeModal()
      return true
    }
    return goBack()
  }

  function today() {
    const now = new Date()
    return now.getFullYear() + '-' +
      String(now.getMonth() + 1).padStart(2, '0') + '-' +
      String(now.getDate()).padStart(2, '0')
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;')
  }

  function parseAmount(input) {
    const text = String(input == null ? '' : input).trim()
    if (!text) return { ok: false, msg: '请输入金额' }
    if (!/^\d+(\.\d{1,2})?$/.test(text)) {
      return { ok: false, msg: '金额需为大于 0 的数字，最多两位小数' }
    }
    const cents = Math.round(Number(text) * 100)
    if (cents <= 0) return { ok: false, msg: '金额需大于 0' }
    if (cents > 999999999) return { ok: false, msg: '金额超出上限' }
    return { ok: true, cents: cents }
  }

  function formatCents(cents) {
    return (Number(cents || 0) / 100).toFixed(2)
  }

  function formatDate(date) {
    return String(date || '').replace(/-/g, '.')
  }

  function dayMonth(date) {
    const parts = String(date || '').split('-')
    return {
      day: parts[2] || '--',
      month: parts[1] ? Number(parts[1]) + '月' : ''
    }
  }

  function statusClass(value) {
    return 'status-' + String(value || 'ongoing').replace(/[^a-z]/g, '')
  }

  function toast(message) {
    window.clearTimeout(toastTimer)
    toastElement.textContent = message
    toastElement.hidden = false
    toastTimer = window.setTimeout(function () {
      toastElement.hidden = true
    }, 2200)
  }

  window.TravelExpenseUi = { toast: toast }

  function openModal(html) {
    modalSheet.innerHTML = html
    modal.hidden = false
    const autofocus = modalSheet.querySelector('[autofocus]')
    if (autofocus) window.setTimeout(function () { autofocus.focus() }, 30)
  }

  function closeModal() {
    modal.hidden = true
    modalSheet.innerHTML = ''
  }

  function showConfirm(options) {
    openModal(
      '<h2 id="modalTitle">' + escapeHtml(options.title) + '</h2>' +
      '<p class="modal-copy">' + escapeHtml(options.message) + '</p>' +
      '<div class="sheet-actions horizontal">' +
      '<button class="secondary-button" type="button" data-modal-cancel>' +
      escapeHtml(options.cancelText || '取消') + '</button>' +
      '<button class="' + (options.danger ? 'danger-button' : 'primary-button') +
      '" type="button" data-confirm>' + escapeHtml(options.confirmText || '确定') +
      '</button></div>'
    )
    const confirmButton = modalSheet.querySelector('[data-confirm]')
    confirmButton.addEventListener('click', function () {
      closeModal()
      if (options.onConfirm) options.onConfirm()
    })
  }

  function showInput(options) {
    openModal(
      '<h2 id="modalTitle">' + escapeHtml(options.title) + '</h2>' +
      '<form id="modalForm" class="form-block">' +
      '<label class="form-label" for="modalInput">' + escapeHtml(options.label) + '</label>' +
      '<input id="modalInput" class="form-input" maxlength="12" autocomplete="off" ' +
      'value="' + escapeHtml(options.value || '') + '" autofocus>' +
      '<div class="sheet-actions horizontal">' +
      '<button class="secondary-button" type="button" data-modal-cancel>取消</button>' +
      '<button class="primary-button" type="submit">保存</button></div></form>'
    )
    const form = modalSheet.querySelector('#modalForm')
    const input = modalSheet.querySelector('#modalInput')
    form.addEventListener('submit', function (event) {
      event.preventDefault()
      const result = options.onSave(input.value)
      if (result && result.ok === false) {
        toast(result.msg)
        return
      }
      closeModal()
    })
  }

  function statusMarkup(status) {
    return '<span class="status-pill ' + statusClass(status.value) + '">' +
      escapeHtml(status.label) + '</span>'
  }

  function renderTrips() {
    const trips = store.getTrips()
    const allExpenses = store.getAllExpenses()
    const rows = trips.map(function (trip) {
      const expenses = store.getExpenses(trip.id)
      const status = store.statusOf(trip.status)
      return '<button class="row-card" type="button" data-action="open-trip" data-id="' +
        escapeHtml(trip.id) + '">' +
        '<span class="row-card-main"><span class="row-title">' + escapeHtml(trip.name) +
        '</span><span class="row-note">' + escapeHtml(formatDate(trip.startDate)) +
        ' — ' + escapeHtml(formatDate(trip.endDate)) + ' · ' + expenses.length +
        ' 笔</span></span>' +
        '<span class="row-right"><span class="money">¥' +
        formatCents(store.totalCents(expenses)) + '</span><br>' +
        statusMarkup(status) + '</span><span class="chevron">›</span></button>'
    }).join('')

    return '<section class="page">' +
      '<div class="hero"><div class="hero-row"><div>' +
      '<div class="eyebrow">累计支出</div>' +
      '<div class="hero-amount">¥' + formatCents(store.totalCents(allExpenses)) + '</div>' +
      '<div class="hero-meta">' + trips.length + ' 个行程 · ' +
      allExpenses.length + ' 笔记录</div></div>' +
      '<button class="primary-button hero-action" type="button" data-action="new-trip">' +
      '新建行程</button></div></div>' +
      '<div class="section-head"><span>全部行程</span><span>' + trips.length + ' 个</span></div>' +
      (rows
        ? '<div class="list-card">' + rows + '</div>'
        : '<div class="empty-state"><span class="empty-mark">＋</span>' +
          '<span class="empty-title">还没有出差记录</span>' +
          '<span class="empty-note">新建一个行程后开始记账</span></div>') +
      '</section>'
  }

  function renderNewTrip() {
    if (!tripFormDates) tripFormDates = { startDate: today(), endDate: today() }
    return '<section class="page">' +
      '<form id="tripForm" class="form-card">' +
      '<div class="form-block"><label class="form-label" for="tripName">行程名称</label>' +
      '<input id="tripName" name="name" class="form-input" maxlength="40" ' +
      'placeholder="例如：上海客户拜访" autofocus></div>' +
      '<div class="form-block"><label class="form-label" for="tripStart">开始日期</label>' +
      '<input id="tripStart" name="startDate" class="form-date" type="date" value="' +
      escapeHtml(tripFormDates.startDate) + '"></div>' +
      '<div class="form-block"><label class="form-label" for="tripEnd">结束日期</label>' +
      '<input id="tripEnd" name="endDate" class="form-date" type="date" value="' +
      escapeHtml(tripFormDates.endDate) + '"></div>' +
      '<div class="form-footer"><button class="primary-button" type="submit">保存行程</button></div>' +
      '</form></section>'
  }

  function renderTripDetail(route) {
    const trip = store.getTrip(route.tripId)
    if (!trip) return '<section class="page"><div class="empty-state">' +
      '<span class="empty-title">行程不存在</span></div></section>'
    const expenses = store.getExpenses(trip.id)
    const categories = store.categorySummary(expenses).filter(function (row) {
      return row.cents > 0
    })
    const status = store.statusOf(trip.status)
    const readonly = store.isReadonly(trip)
    const noInvoice = expenses.filter(function (item) {
      return !store.hasInvoiceOf(item)
    }).length
    const categoryRows = categories.map(function (row) {
      const max = categories[0] ? categories[0].cents : 1
      const width = max > 0 ? Math.max(5, Math.round(row.cents / max * 100)) : 0
      return '<div class="category-row"><span>' + escapeHtml(row.category) +
        '</span><progress class="bar-progress" max="100" value="' + width +
        '"></progress><span class="money">¥' + formatCents(row.cents) + '</span></div>'
    }).join('')
    const expenseRows = expenses.map(function (item) {
      const dm = dayMonth(item.date)
      const category = store.categoryName(store.categoryIdOfExpense(item), item.category)
      return '<button class="expense-card" type="button" data-action="edit-expense" data-id="' +
        escapeHtml(item.id) + '"><span class="date-tile"><strong>' + escapeHtml(dm.day) +
        '</strong>' + escapeHtml(dm.month) + '</span><span><span class="row-title">' +
        escapeHtml(category) + '</span><span class="expense-note">' +
        escapeHtml(item.note || '无备注') + '</span></span><span class="row-right">' +
        '<span class="money">¥' + formatCents(item.amountCents) + '</span><br>' +
        '<span class="muted-small ' + (store.hasInvoiceOf(item) ? '' : 'invoice-no') + '">' +
        (store.hasInvoiceOf(item) ? '有发票' : '无发票') + '</span></span></button>'
    }).join('')

    return '<section class="page">' +
      '<div class="detail-head"><div><div class="detail-title">' + escapeHtml(trip.name) +
      '</div><div class="detail-date">' + escapeHtml(formatDate(trip.startDate)) +
      ' — ' + escapeHtml(formatDate(trip.endDate)) + '</div></div>' +
      '<button class="status-button" type="button" data-action="status-menu">' +
      statusMarkup(status) + '</button></div>' +
      '<div class="detail-summary"><div class="eyebrow">本次合计</div><strong>¥' +
      formatCents(store.totalCents(expenses)) + '</strong><small>' + expenses.length +
      ' 笔 · ' + categories.length + ' 个类目' +
      (noInvoice > 0 ? ' · ' + noInvoice + ' 笔无发票' : '') + '</small></div>' +
      '<div class="section-head"><span>类目小计</span>' +
      '<button type="button" data-action="trip-stats">当前行程统计 ›</button></div>' +
      (categoryRows ? '<div class="category-summary">' + categoryRows + '</div>' :
        '<div class="note-card"><p>还没有支出记录</p></div>') +
      '<div class="section-head"><span>支出明细</span><span>' + expenses.length +
      ' 笔</span></div>' +
      (expenseRows ? '<div class="list-card">' + expenseRows + '</div>' :
        '<div class="empty-state"><span class="empty-mark">＋</span>' +
        '<span class="empty-title">这次出差还没记账</span>' +
        '<span class="empty-note">点击下方按钮添加第一笔支出</span></div>') +
      '<div class="sticky-footer"><button class="primary-button width-full" ' +
      'type="button" data-action="new-expense"' + (readonly ? ' disabled' : '') +
      '>记一笔</button>' +
      (readonly ? '<p class="muted-small center-note compact">' +
        '已收款行程为只读状态</p>' : '') +
      '</div></section>'
  }

  function renderExpenseForm(route) {
    let trip = null
    let expense = null
    if (route.expenseId) {
      expense = store.getExpense(route.expenseId)
      if (expense) trip = store.getTrip(expense.tripId)
    } else if (route.tripId) {
      trip = store.getTrip(route.tripId)
    }
    if (!trip || (expense && store.isReadonly(trip))) {
      return '<section class="page"><div class="empty-state">' +
        '<span class="empty-title">记录不可编辑</span></div></section>'
    }
    const categories = store.getCategories()
    if (!selectedCategoryId || !categories.some(function (cat) {
      return cat.id === selectedCategoryId
    })) {
      selectedCategoryId = expense
        ? store.categoryIdOfExpense(expense)
        : (categories[0] ? categories[0].id : store.OTHER_CATEGORY_ID)
    }
    const chips = categories.map(function (category) {
      return '<button type="button" class="choice-chip' +
        (category.id === selectedCategoryId ? ' active' : '') +
        '" data-action="select-category" data-id="' + escapeHtml(category.id) +
        '">' + escapeHtml(category.name) + '</button>'
    }).join('')
    const amount = expense ? formatCents(expense.amountCents) : ''
    const date = expense ? expense.date : today()
    const note = expense ? expense.note : ''
    const hasInvoice = expense ? store.hasInvoiceOf(expense) : true

    return '<section class="page">' +
      '<form id="expenseForm" class="form-card">' +
      '<div class="form-block"><div class="muted-small">' + escapeHtml(trip.name) + '</div>' +
      '<label class="form-label" for="expenseAmount">金额（元）</label>' +
      '<input id="expenseAmount" name="amount" class="form-input amount-input" ' +
      'inputmode="decimal" maxlength="12" placeholder="0.00" value="' +
      escapeHtml(amount) + '" autofocus></div>' +
      '<div class="form-block"><span class="form-label">类目</span>' +
      '<div class="chip-grid">' + chips + '</div></div>' +
      '<div class="form-block"><label class="form-label" for="expenseDate">日期</label>' +
      '<input id="expenseDate" name="date" class="form-date" type="date" value="' +
      escapeHtml(date) + '"></div>' +
      '<div class="form-block"><label class="form-label" for="expenseNote">备注</label>' +
      '<textarea id="expenseNote" name="note" class="form-textarea" maxlength="120" ' +
      'placeholder="选填，如客户晚餐">' + escapeHtml(note) + '</textarea></div>' +
      '<div class="form-block"><label class="check-row"><span>已取得发票</span>' +
      '<input id="hasInvoice" name="hasInvoice" type="checkbox"' +
      (hasInvoice ? ' checked' : '') + '></label></div>' +
      '<div class="form-footer"><button class="primary-button" type="submit">' +
      (expense ? '保存修改' : '保存支出') + '</button></div>' +
      '</form></section>'
  }

  function renderStats(route) {
    let expenses = store.getAllExpenses()
    let trips = store.getTrips()
    let trip = null
    if (route.tripId) {
      trip = store.getTrip(route.tripId)
      if (!trip) return '<section class="page"><div class="empty-state">' +
        '<span class="empty-title">行程不存在</span></div></section>'
      expenses = store.getExpenses(trip.id)
    }
    const summary = store.categorySummary(expenses).filter(function (row) {
      return row.count > 0
    })
    const maxCents = summary.reduce(function (max, row) {
      return Math.max(max, row.cents)
    }, 1)
    const rows = summary.map(function (row) {
      const width = Math.max(5, Math.round(row.cents / maxCents * 100))
      return '<div class="category-row"><span>' + escapeHtml(row.category) +
        '</span><progress class="bar-progress" max="100" value="' + width +
        '"></progress><span class="money">' + row.percent + '%</span></div>'
    }).join('')
    return '<section class="page">' +
      '<div class="hero"><div class="hero-row"><div><div class="eyebrow">' +
      (trip ? escapeHtml(trip.name) : '累计支出') + '</div>' +
      '<div class="hero-amount">¥' + formatCents(store.totalCents(expenses)) + '</div>' +
      '<div class="hero-meta">' + (trip ? '1 次出差' : trips.length + ' 次出差') +
      ' · ' + expenses.length + ' 笔支出</div></div></div></div>' +
      '<div class="section-head"><span>类目占比</span><span>' +
      (expenses.length ? summary.length + ' 项' : '') + '</span></div>' +
      (rows ? '<div class="category-summary">' + rows + '</div>' :
        '<div class="empty-state"><span class="empty-mark">＋</span>' +
        '<span class="empty-title">暂无数据</span>' +
        '<span class="empty-note">记一笔后这里会出现类目汇总</span></div>') +
      '<div class="section-head"><span>数据</span></div>' +
      '<button class="secondary-button width-full" type="button" ' +
      'data-action="export"' + (expenses.length === 0 ? ' disabled' : '') + '>' +
      (trip ? '导出当前行程 Excel' : '导出所有行程 Excel') + '</button>' +
      '<p class="muted-small center-note compact">' +
      '文件只在本机生成，可通过系统分享保存</p></section>'
  }

  function renderSettings() {
    const tripCount = store.getTrips().length
    const expenseCount = store.getAllExpenses().length
    return '<section class="page">' +
      '<div class="settings-group">' +
      '<button class="settings-row" type="button" data-action="categories">' +
      '<span><span class="settings-title">类目管理</span>' +
      '<span class="settings-note">增删、改名、排序与恢复默认</span></span>' +
      '<span class="settings-value">' + store.getCategories().length + ' 项</span>' +
      '<span class="chevron">›</span></button>' +
      '<button class="settings-row" type="button" data-action="about">' +
      '<span><span class="settings-title">关于工具</span>' +
      '<span class="settings-note">使用说明与数据说明</span></span>' +
      '<span class="chevron">›</span></button>' +
      '</div>' +
      '<div class="note-card"><h2>本机数据</h2>' +
      '<p>共 ' + tripCount + ' 个行程、' + expenseCount +
      ' 笔支出。数据只保存在当前设备，不会上传。</p>' +
      '<button class="danger-button width-full spaced-top" type="button" ' +
      'data-action="clear-all">清空所有数据</button></div>' +
      '<p class="muted-small center-note">' +
      '出差报销随手记 · 工具箱适配版 ' + TOOL_VERSION + '</p></section>'
  }

  function renderCategories() {
    const categories = store.getCategories()
    const rows = categories.map(function (category, index) {
      const count = store.countExpensesOfCategory(category.id)
      return '<div class="category-manage-row"><div><div class="row-title">' +
        escapeHtml(category.name) + '</div><div class="row-note">' + count + ' 笔' +
        (category.builtin ? ' · 内置' : '') + '</div></div>' +
        '<div class="category-actions">' +
        '<button class="icon-action" type="button" data-action="move-category" ' +
        'data-id="' + escapeHtml(category.id) + '" data-direction="up"' +
        (index === 0 ? ' disabled' : '') + ' aria-label="上移">↑</button>' +
        '<button class="icon-action" type="button" data-action="move-category" ' +
        'data-id="' + escapeHtml(category.id) + '" data-direction="down"' +
        (index === categories.length - 1 ? ' disabled' : '') + ' aria-label="下移">↓</button>' +
        '<button class="text-action" type="button" data-action="rename-category" ' +
        'data-id="' + escapeHtml(category.id) + '">改名</button>' +
        (category.id === store.OTHER_CATEGORY_ID ? '' :
          '<button class="text-action danger" type="button" data-action="delete-category" ' +
          'data-id="' + escapeHtml(category.id) + '">删除</button>') +
        '</div></div>'
    }).join('')
    return '<section class="page">' +
      '<p class="muted-small">类目顺序会同步到记账、统计和 Excel 导出。</p>' +
      '<div class="settings-group">' + rows + '</div>' +
      '<div class="inline-actions"><button class="primary-button" type="button" ' +
      'data-action="add-category">新增类目</button>' +
      '<button class="secondary-button" type="button" data-action="reset-categories">' +
      '恢复默认</button></div></section>'
  }

  function renderAbout() {
    return '<section class="page">' +
      '<div class="note-card"><h2>出差报销随手记</h2>' +
      '<p>按行程记录开销，在报销前快速看清花了多少、钱花在哪些类目。</p></div>' +
      '<div class="note-card"><h2>使用方法</h2>' +
      '<p>1. 新建行程，填写名称和起止日期。<br>' +
      '2. 在行程详情中逐笔记录支出。<br>' +
      '3. 按报销进度切换行程状态，已收款后不可修改。<br>' +
      '4. 在统计页查看占比并导出 Excel。</p></div>' +
      '<div class="note-card"><h2>数据说明</h2>' +
      '<p>所有记录只保存在当前设备，不联网、不上传。清理应用数据或卸载应用会丢失记录，请定期导出表格。</p></div>' +
      '<p class="muted-small center-note">' +
      '工具箱适配版 ' + TOOL_VERSION + '</p></section>'
  }

  function renderNotFound() {
    return '<section class="page"><div class="empty-state">' +
      '<span class="empty-title">页面不存在</span></div></section>'
  }

  const routeTitles = {
    trips: '出差报销随手记',
    'trip-new': '新建行程',
    trip: '行程详情',
    'expense-new': '记一笔',
    'expense-edit': '编辑支出',
    stats: '统计',
    settings: '设置',
    categories: '类目管理',
    about: '关于工具'
  }

  function render() {
    const route = currentRoute()
    pageTitle.textContent = routeTitles[route.name] || '出差报销随手记'
    topAction.hidden = route.name !== 'trip'
    tabbar.hidden = !TOP_LEVEL.has(route.name)
    tabbar.querySelectorAll('[data-tab]').forEach(function (button) {
      button.classList.toggle('active', button.dataset.tab === route.name)
    })

    if (route.name === 'trips') view.innerHTML = renderTrips()
    else if (route.name === 'trip-new') view.innerHTML = renderNewTrip()
    else if (route.name === 'trip') view.innerHTML = renderTripDetail(route)
    else if (route.name === 'expense-new' || route.name === 'expense-edit') {
      view.innerHTML = renderExpenseForm(route)
    } else if (route.name === 'stats') {
      pageTitle.textContent = route.tripId ? '行程统计' : '统计'
      view.innerHTML = renderStats(route)
    }
    else if (route.name === 'settings') view.innerHTML = renderSettings()
    else if (route.name === 'categories') view.innerHTML = renderCategories()
    else if (route.name === 'about') view.innerHTML = renderAbout()
    else view.innerHTML = renderNotFound()

    view.scrollTop = 0
  }

  function showStatusMenu() {
    const route = currentRoute()
    const trip = store.getTrip(route.tripId)
    if (!trip) return
    const buttons = store.STATUSES.map(function (status) {
      return '<button class="' + (status.value === trip.status ? 'primary-button' :
        'secondary-button') + '" type="button" data-modal-status="' +
        escapeHtml(status.value) + '">' + escapeHtml(status.label) + '</button>'
    }).join('')
    openModal(
      '<h2 id="modalTitle">修改行程状态</h2>' +
      '<p class="modal-copy">已收款状态只读，导出和查看不受影响。</p>' +
      '<div class="sheet-actions">' + buttons +
      '<button class="danger-button" type="button" data-modal-delete-trip>' +
      '删除本次行程</button></div>'
    )
    modalSheet.querySelectorAll('[data-modal-status]').forEach(function (button) {
      button.addEventListener('click', function () {
        const status = store.statusOf(button.dataset.modalStatus)
        store.updateTrip(trip.id, { status: status.value })
        closeModal()
        render()
        toast('已切换为' + status.label)
      })
    })
    modalSheet.querySelector('[data-modal-delete-trip]').addEventListener('click', function () {
      closeModal()
      confirmDeleteTrip(trip)
    })
  }

  function confirmDeleteTrip(trip) {
    const expenses = store.getExpenses(trip.id)
    const total = formatCents(store.totalCents(expenses))
    showConfirm({
      title: '删除行程「' + trip.name + '」？',
      message: expenses.length > 0
        ? '该行程下有 ' + expenses.length + ' 笔记录，合计 ¥' + total + '，删除后不可撤销。'
        : '删除后不可撤销。',
      confirmText: '删除',
      danger: true,
      onConfirm: function () {
        store.deleteTrip(trip.id)
        goBack()
        toast('行程已删除')
      }
    })
  }

  function handleAction(action, button) {
    if (action === 'new-trip') {
      tripFormDates = { startDate: today(), endDate: today() }
      navigate('trip-new')
    } else if (action === 'open-trip') {
      navigate('trip', { tripId: button.dataset.id })
    } else if (action === 'trip-stats') {
      navigate('stats', { tripId: currentRoute().tripId })
    } else if (action === 'new-expense') {
      selectedCategoryId = ''
      navigate('expense-new', { tripId: currentRoute().tripId })
    } else if (action === 'edit-expense') {
      const trip = store.getTrip(currentRoute().tripId)
      if (store.isReadonly(trip)) {
        toast('已收款行程不可修改，请先将状态改为已归档')
        return
      }
      selectedCategoryId = ''
      navigate('expense-edit', { expenseId: button.dataset.id })
    } else if (action === 'status-menu') {
      showStatusMenu()
    } else if (action === 'select-category') {
      selectedCategoryId = button.dataset.id
      view.querySelectorAll('[data-action="select-category"]').forEach(function (chip) {
        chip.classList.toggle('active', chip.dataset.id === selectedCategoryId)
      })
    } else if (action === 'export') {
      const route = currentRoute()
      if (route.tripId) excel.exportTrip(route.tripId)
      else excel.exportAllTrips()
    } else if (action === 'categories') {
      navigate('categories')
    } else if (action === 'about') {
      navigate('about')
    } else if (action === 'add-category') {
      showInput({
        title: '新增类目',
        label: '类目名称',
        value: '',
        onSave: function (value) {
          const result = store.addCategory(value)
          if (result.ok) {
            render()
            toast('类目已添加')
          }
          return result
        }
      })
    } else if (action === 'rename-category') {
      const category = store.getCategories().filter(function (item) {
        return item.id === button.dataset.id
      })[0]
      if (!category) return
      showInput({
        title: '修改类目名称',
        label: '类目名称',
        value: category.name,
        onSave: function (value) {
          const result = store.renameCategory(category.id, value)
          if (result.ok) {
            render()
            toast('类目名称已修改')
          }
          return result
        }
      })
    } else if (action === 'move-category') {
      const result = store.moveCategory(button.dataset.id, button.dataset.direction)
      if (result.ok) render()
      else toast(result.msg)
    } else if (action === 'delete-category') {
      const category = store.getCategories().filter(function (item) {
        return item.id === button.dataset.id
      })[0]
      if (!category) return
      const count = store.countExpensesOfCategory(category.id)
      showConfirm({
        title: '删除类目「' + category.name + '」？',
        message: count > 0
          ? '该类目下有 ' + count + ' 笔记录，删除后将并入「其他」，不可撤销。'
          : '删除后不可撤销。',
        confirmText: '删除',
        danger: true,
        onConfirm: function () {
          const result = store.deleteCategory(category.id)
          render()
          toast(result.merged > 0 ? result.merged + ' 笔已并入「其他」' : '类目已删除')
        }
      })
    } else if (action === 'reset-categories') {
      showConfirm({
        title: '恢复默认内置配置？',
        message: '内置类目会恢复默认名称和顺序，自定义类目保留在后，已有记录不受影响。',
        confirmText: '恢复',
        onConfirm: function () {
          const result = store.resetCategories()
          render()
          toast(result.merged > 0 ? result.merged + ' 个同名类目已并入内置' : '已恢复默认')
        }
      })
    } else if (action === 'clear-all') {
      confirmClearAll()
    }
  }

  function confirmClearAll() {
    const trips = store.getTrips().length
    const expenses = store.getAllExpenses().length
    showConfirm({
      title: '清空所有数据？',
      message: '将删除 ' + trips + ' 个行程、' + expenses +
        ' 笔支出，类目恢复默认 6 项。建议先导出表格备份。',
      confirmText: '继续',
      danger: true,
      onConfirm: function () {
        showConfirm({
          title: '确认清空？',
          message: '此操作不可撤销，本机没有回收站。',
          confirmText: '清空',
          danger: true,
          onConfirm: function () {
            store.clearAll()
            render()
            toast('数据已清空')
          }
        })
      }
    })
  }

  function handleSubmit(event) {
    const form = event.target
    if (form.id === 'tripForm') {
      event.preventDefault()
      const data = new FormData(form)
      const name = String(data.get('name') || '').trim()
      const startDate = String(data.get('startDate') || '')
      const endDate = String(data.get('endDate') || '')
      if (!name) return toast('请填写行程名')
      if (!startDate || !endDate) return toast('请选择起止日期')
      if (endDate < startDate) return toast('结束日期不能早于开始日期')
      const trip = store.createTrip({ name: name, startDate: startDate, endDate: endDate })
      tripFormDates = null
      routeStack.pop()
      navigate('trip', { tripId: trip.id })
      toast('行程已创建')
    } else if (form.id === 'expenseForm') {
      event.preventDefault()
      const route = currentRoute()
      const data = new FormData(form)
      const amount = parseAmount(data.get('amount'))
      if (!amount.ok) return toast(amount.msg)
      const payload = {
        categoryId: selectedCategoryId || store.OTHER_CATEGORY_ID,
        amountCents: amount.cents,
        date: String(data.get('date') || today()),
        note: String(data.get('note') || '').trim(),
        hasInvoice: data.get('hasInvoice') === 'on'
      }
      if (route.name === 'expense-edit') {
        store.updateExpense(route.expenseId, payload)
      } else {
        store.createExpense(Object.assign({ tripId: route.tripId }, payload))
      }
      goBack()
      toast('支出已保存')
    }
  }

  view.addEventListener('click', function (event) {
    const actionButton = event.target.closest('[data-action]')
    if (!actionButton) return
    handleAction(actionButton.dataset.action, actionButton)
  })
  view.addEventListener('submit', handleSubmit)

  tabbar.addEventListener('click', function (event) {
    const button = event.target.closest('[data-tab]')
    if (!button) return
    resetRoute(button.dataset.tab)
  })

  backButton.addEventListener('click', function () {
    if (!modal.hidden) {
      closeModal()
      return
    }
    if (!goBack() && window.AndroidToolbox && window.AndroidToolbox.closeTool) {
      window.AndroidToolbox.closeTool()
    }
  })

  topAction.addEventListener('click', function () {
    if (currentRoute().name === 'trip') showStatusMenu()
  })

  modal.addEventListener('click', function (event) {
    if (event.target.closest('[data-modal-cancel]')) closeModal()
  })

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && !modal.hidden) closeModal()
  })

  render()
})()

