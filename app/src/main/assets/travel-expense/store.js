(function (root) {
const TRIPS_KEY = 'travel_expense_trips'
const EXPENSES_KEY = 'ten_expenses'
const CATEGORIES_KEY = 'travel_expense_categories'

const OTHER_CATEGORY_ID = 'cat_other'

// id 固定不变，只换名字：cat_transport 原名「交通」，cat_city 原名「市内交通」
const DEFAULT_CATEGORIES = [
  { id: 'cat_city', name: '打车', order: 0, builtin: true },
  { id: 'cat_meal', name: '餐饮', order: 1, builtin: true },
  { id: 'cat_transport', name: '火车飞机', order: 2, builtin: true },
  { id: 'cat_hotel', name: '住宿', order: 3, builtin: true },
  { id: 'cat_insurance', name: '出行保险', order: 4, builtin: true },
  { id: 'cat_other', name: '其他', order: 5, builtin: true }
]

// v1 支出只存类目文字，迁移时按这张表反查 id
const LEGACY_NAME_TO_ID = {
  交通: 'cat_transport',
  住宿: 'cat_hotel',
  餐饮: 'cat_meal',
  市内交通: 'cat_city',
  其他: 'cat_other'
}

// 内置项改名前的名字，只有仍是旧名才自动换成新名
const BUILTIN_LEGACY_NAME = {
  cat_transport: '交通',
  cat_hotel: '住宿',
  cat_meal: '餐饮',
  cat_city: '市内交通',
  cat_other: '其他'
}

const STATUSES = [
  { value: 'ongoing', label: '进行中', color: '#0052d9' },
  { value: 'submitted', label: '已提单', color: '#ed7b2f' },
  { value: 'archived', label: '已归档', color: '#00a870' },
  { value: 'paid', label: '已收款', color: '#646a73' }
]

const READONLY_STATUS = 'paid'

function genId() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
}

const storage = {
  get: function (key) {
    const text = localStorage.getItem(key)
    if (text == null) return ''
    try {
      return JSON.parse(text)
    } catch (e) {
      return text
    }
  },
  set: function (key, value) {
    localStorage.setItem(key, JSON.stringify(value))
  },
  remove: function (key) {
    localStorage.removeItem(key)
  }
}

function readList(key) {
  try {
    const data = storage.get(key)
    return Array.isArray(data) ? data : []
  } catch (e) {
    return []
  }
}

function writeList(key, list) {
  storage.set(key, list)
}

function statusRank(value) {
  const index = STATUSES.map((item) => item.value).indexOf(value)
  return index < 0 ? STATUSES.length : index
}

function getTrips() {
  return readList(TRIPS_KEY)
    .slice()
    .sort((a, b) => {
      const byStatus = statusRank(a.status) - statusRank(b.status)
      if (byStatus !== 0) return byStatus
      if (a.startDate !== b.startDate) return a.startDate < b.startDate ? 1 : -1
      return b.createdAt - a.createdAt
    })
}

function getTrip(tripId) {
  return getTrips().filter((trip) => trip.id === tripId)[0] || null
}

function createTrip(data) {
  const now = Date.now()
  const trip = {
    id: genId(),
    name: data.name,
    startDate: data.startDate,
    endDate: data.endDate,
    status: 'ongoing',
    createdAt: now
  }
  const list = readList(TRIPS_KEY)
  list.push(trip)
  writeList(TRIPS_KEY, list)
  return trip
}

function updateTrip(tripId, patch) {
  const list = readList(TRIPS_KEY)
  const index = list.map((item) => item.id).indexOf(tripId)
  if (index < 0) return null
  list[index] = Object.assign({}, list[index], patch)
  writeList(TRIPS_KEY, list)
  return list[index]
}

function deleteTrip(tripId) {
  const list = readList(TRIPS_KEY)
  const index = list.map((item) => item.id).indexOf(tripId)
  if (index < 0) return null
  list.splice(index, 1)
  writeList(TRIPS_KEY, list)
  const expenses = readList(EXPENSES_KEY)
  const kept = expenses.filter((item) => item.tripId !== tripId)
  writeList(EXPENSES_KEY, kept)
  return { ok: true, removed: expenses.length - kept.length }
}

function getExpenses(tripId) {
  return readList(EXPENSES_KEY)
    .filter((item) => item.tripId === tripId)
    .sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : b.createdAt - a.createdAt))
}

function getAllExpenses() {
  return readList(EXPENSES_KEY).slice()
}

function createExpense(data) {
  const expense = {
    id: genId(),
    tripId: data.tripId,
    categoryId: data.categoryId,
    amountCents: data.amountCents,
    date: data.date,
    note: data.note || '',
    hasInvoice: data.hasInvoice !== false,
    createdAt: Date.now()
  }
  const list = readList(EXPENSES_KEY)
  list.push(expense)
  writeList(EXPENSES_KEY, list)
  return expense
}

// 旧记录没有 hasInvoice 字段，按「有发票」处理
function hasInvoiceOf(expense) {
  return !!expense && expense.hasInvoice !== false
}

function getExpense(expenseId) {
  return getAllExpenses().filter((item) => item.id === expenseId)[0] || null
}

function updateExpense(expenseId, patch) {
  const list = readList(EXPENSES_KEY)
  const index = list.map((item) => item.id).indexOf(expenseId)
  if (index < 0) return null
  list[index] = Object.assign({}, list[index], patch)
  writeList(EXPENSES_KEY, list)
  return list[index]
}

function deleteExpense(expenseId) {
  const list = readList(EXPENSES_KEY)
  const index = list.map((item) => item.id).indexOf(expenseId)
  if (index < 0) return false
  list.splice(index, 1)
  writeList(EXPENSES_KEY, list)
  return true
}

function totalCents(expenses) {
  return expenses.reduce((sum, item) => sum + item.amountCents, 0)
}

// 清空全部业务数据：行程、支出、类目；迁移标记保留（已无旧数据可迁）
function clearAll() {
  ;[TRIPS_KEY, EXPENSES_KEY, CATEGORIES_KEY].forEach((key) => {
    storage.remove(key)
  })
  return { ok: true }
}

// ---------- 类目 ----------

function getCategories() {
  const list = readList(CATEGORIES_KEY)
  const source = list.length > 0 ? list : DEFAULT_CATEGORIES
  return source
    .slice()
    .sort((a, b) => a.order - b.order)
}

function categoryName(categoryId, legacyName) {
  const hit = getCategories().filter((item) => item.id === categoryId)[0]
  if (hit) return hit.name
  return legacyName || '其他'
}

// 兼容 v1 记录：只存了类目文字的支出按名称反查 id
function categoryIdOfExpense(expense) {
  if (expense.categoryId) return expense.categoryId
  const hit = getCategories().filter((item) => item.name === expense.category)[0]
  return hit ? hit.id : OTHER_CATEGORY_ID
}

function addCategory(name) {
  const text = String(name == null ? '' : name).trim()
  if (!text) return { ok: false, msg: '请输入类目名称' }
  if (text.length > 12) return { ok: false, msg: '类目名称最多 12 个字' }
  const list = getCategories()
  if (list.some((item) => item.name === text)) return { ok: false, msg: '已有同名类目' }
  const created = {
    id: 'cat_' + genId(),
    name: text,
    order: list.length > 0 ? list[list.length - 1].order + 1 : 0,
    builtin: false
  }
  list.push(created)
  writeList(CATEGORIES_KEY, list)
  return { ok: true, category: created }
}

function renameCategory(categoryId, name) {
  const text = String(name == null ? '' : name).trim()
  if (!text) return { ok: false, msg: '请输入类目名称' }
  if (text.length > 12) return { ok: false, msg: '类目名称最多 12 个字' }
  const list = getCategories()
  if (list.some((item) => item.id !== categoryId && item.name === text)) {
    return { ok: false, msg: '已有同名类目' }
  }
  const index = list.map((item) => item.id).indexOf(categoryId)
  if (index < 0) return { ok: false, msg: '类目不存在' }
  list[index].name = text
  writeList(CATEGORIES_KEY, list)
  return { ok: true }
}

function moveCategory(categoryId, direction) {
  const list = getCategories()
  const index = list.map((item) => item.id).indexOf(categoryId)
  if (index < 0) return { ok: false, msg: '类目不存在' }
  const target = index + (direction === 'up' ? -1 : 1)
  if (target < 0 || target >= list.length) return { ok: false, msg: '已经到头了' }
  const moved = list[index]
  list[index] = list[target]
  list[target] = moved
  list.forEach((item, i) => {
    item.order = i
  })
  writeList(CATEGORIES_KEY, list)
  return { ok: true }
}

function countExpensesOfCategory(categoryId) {
  return getAllExpenses().filter((item) => categoryIdOfExpense(item) === categoryId).length
}

function deleteCategory(categoryId) {
  if (categoryId === OTHER_CATEGORY_ID) return { ok: false, msg: '「其他」是并入目标，不能删除' }
  const list = getCategories()
  const index = list.map((item) => item.id).indexOf(categoryId)
  if (index < 0) return { ok: false, msg: '类目不存在' }
  const affected = getAllExpenses().filter((item) => categoryIdOfExpense(item) === categoryId)
  if (affected.length > 0) {
    const ids = affected.map((item) => item.id)
    const expenses = readList(EXPENSES_KEY).map((item) => {
      if (ids.indexOf(item.id) < 0) return item
      return Object.assign({}, item, { categoryId: OTHER_CATEGORY_ID })
    })
    writeList(EXPENSES_KEY, expenses)
  }
  list.splice(index, 1)
  list.forEach((item, i) => {
    item.order = i
  })
  writeList(CATEGORIES_KEY, list)
  return { ok: true, merged: affected.length }
}

// 恢复内置类目的默认名与顺序；自定义类目保留并排在后面，同名自定义并入内置项
function resetCategories() {
  const list = getCategories()
  const defaults = DEFAULT_CATEGORIES.map((item) => Object.assign({}, item))
  const builtinIds = defaults.map((item) => item.id)
  const customs = list
    .filter((cat) => builtinIds.indexOf(cat.id) < 0)
    .sort((a, b) => a.order - b.order)

  const mergeTarget = {}
  customs.forEach((cat) => {
    const def = defaults.filter((item) => item.name === cat.name)[0]
    if (def) mergeTarget[cat.id] = def.id
  })
  const mergedIds = Object.keys(mergeTarget)
  if (mergedIds.length > 0) {
    const expenses = readList(EXPENSES_KEY).map((item) => {
      const categoryId = categoryIdOfExpense(item)
      return mergeTarget[categoryId]
        ? Object.assign({}, item, { categoryId: mergeTarget[categoryId] })
        : item
    })
    writeList(EXPENSES_KEY, expenses)
  }

  const merged = defaults.concat(customs.filter((cat) => !mergeTarget[cat.id]))
  merged.forEach((item, index) => {
    item.order = index
  })
  writeList(CATEGORIES_KEY, merged)
  return { ok: true, merged: mergedIds.length }
}

// ---------- 统计 ----------

function categorySummary(expenses) {
  const categories = getCategories()
  const map = {}
  categories.forEach((item) => {
    map[item.id] = { id: item.id, category: item.name, cents: 0, count: 0 }
  })
  expenses.forEach((item) => {
    const categoryId = categoryIdOfExpense(item)
    if (!map[categoryId]) {
      map[categoryId] = { id: categoryId, category: '其他', cents: 0, count: 0 }
    }
    map[categoryId].cents += item.amountCents
    map[categoryId].count += 1
  })
  const total = totalCents(expenses)
  return categories.map((item) => {
    const row = map[item.id]
    return {
      id: row.id,
      category: row.category,
      cents: row.cents,
      count: row.count,
      percent: total > 0 ? Math.round((row.cents / total) * 1000) / 10 : 0
    }
  })
}

function statusOf(value) {
  return STATUSES.filter((item) => item.value === value)[0] || STATUSES[0]
}

function isReadonly(trip) {
  return !!trip && trip.status === READONLY_STATUS
}

root.TravelExpenseStore = {
  STATUSES,
  READONLY_STATUS,
  OTHER_CATEGORY_ID,
  getTrips,
  getTrip,
  createTrip,
  updateTrip,
  deleteTrip,
  getExpenses,
  getAllExpenses,
  createExpense,
  getExpense,
  updateExpense,
  deleteExpense,
  hasInvoiceOf,
  totalCents,
  clearAll,
  getCategories,
  categoryName,
  categoryIdOfExpense,
  addCategory,
  renameCategory,
  moveCategory,
  countExpensesOfCategory,
  deleteCategory,
  resetCategories,
  categorySummary,
  statusOf,
  isReadonly
}
})(window)
