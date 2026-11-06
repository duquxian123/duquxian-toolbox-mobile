(function (root) {
const store = root.TravelExpenseStore
const xlsx = root.TravelExpenseXlsx

const STYLE_BOLD = 1
const STYLE_MONEY = 2
const SHEET_NAME_MAX = 31
const FILE_NAME_MAX = 40
const ILLEGAL_NAME = /[\\/:*?"<>|\r\n]/g

function pad2(value) {
  return String(value).padStart(2, '0')
}

function stampText(now) {
  const date = now || new Date()
  return '' + date.getFullYear() + pad2(date.getMonth() + 1) + pad2(date.getDate()) +
    '-' + pad2(date.getHours()) + pad2(date.getMinutes())
}

function timeText(timestamp) {
  const date = new Date(timestamp || 0)
  return '' + date.getFullYear() + '-' + pad2(date.getMonth() + 1) + '-' + pad2(date.getDate()) +
    ' ' + pad2(date.getHours()) + ':' + pad2(date.getMinutes())
}

function cleanFileName(name) {
  const text = String(name == null ? '' : name).replace(ILLEGAL_NAME, '').trim()
  if (!text) return '行程'
  return text.slice(0, FILE_NAME_MAX)
}

function cleanSheetName(name, used) {
  let text = String(name == null ? '' : name).replace(/[\\/?*[\]:]/g, '_').trim()
  if (!text) text = 'Sheet'
  text = text.slice(0, SHEET_NAME_MAX)
  let candidate = text
  let counter = 2
  while (used.indexOf(candidate) >= 0) {
    const suffix = '(' + counter + ')'
    candidate = text.slice(0, SHEET_NAME_MAX - suffix.length) + suffix
    counter += 1
  }
  used.push(candidate)
  return candidate
}

function textCell(text, style) {
  return { text: text == null ? '' : String(text), style: style }
}

function numberCell(value, style) {
  return { number: value, style: style }
}

function headerRow(labels) {
  return labels.map(function (label) {
    return textCell(label, STYLE_BOLD)
  })
}

// 单个行程 → 一个 Sheet 的内容
function tripSheet(trip, expenses, categories, sheetName) {
  const summary = store.categorySummary(expenses)
  const total = store.totalCents(expenses)
  const status = store.statusOf(trip.status)
  const rows = []

  rows.push([textCell('格式版本'), textCell('1')])
  rows.push([textCell('行程ID'), textCell(trip.id)])
  rows.push([textCell('行程名称'), textCell(trip.name)])
  rows.push([textCell('起止日期'), textCell(trip.startDate + ' ~ ' + trip.endDate)])
  rows.push([textCell('状态'), textCell(status.label)])
  rows.push([])
  rows.push([textCell('合计金额', STYLE_BOLD), numberCell(total / 100, STYLE_MONEY),
    textCell('笔数', STYLE_BOLD), numberCell(expenses.length)])
  rows.push([])
  rows.push(headerRow(['序号', '日期', '类目', '类目ID', '金额(元)', '金额(分)', '备注', '发票', '支出ID', '创建时间']))

  expenses.forEach(function (item, index) {
    const categoryId = store.categoryIdOfExpense(item)
    rows.push([
      numberCell(index + 1),
      textCell(item.date),
      textCell(store.categoryName(categoryId, item.category)),
      textCell(categoryId),
      numberCell(item.amountCents / 100, STYLE_MONEY),
      numberCell(item.amountCents),
      textCell(item.note),
      textCell(store.hasInvoiceOf(item) ? '有' : '无'),
      textCell(item.id),
      textCell(timeText(item.createdAt))
    ])
  })

  rows.push([])
  rows.push(headerRow(['类目', '金额(元)', '笔数', '', '占比(%)']))
  summary.forEach(function (row) {
    rows.push([
      textCell(row.category),
      numberCell(row.cents / 100, STYLE_MONEY),
      numberCell(row.count),
      textCell(row.id),
      numberCell(row.percent)
    ])
  })

  return {
    name: sheetName,
    rows: rows,
    cols: [
      { min: 4, max: 4, hidden: true, width: 14 },
      { min: 6, max: 6, hidden: true, width: 12 },
      { min: 9, max: 10, hidden: true, width: 18 }
    ]
  }
}

function categorySheet(categories, used) {
  const rows = [headerRow(['类目ID', '类目名称', '排序', '是否内置'])]
  categories.forEach(function (item) {
    rows.push([
      textCell(item.id),
      textCell(item.name),
      numberCell(item.order),
      textCell(item.builtin ? '是' : '否')
    ])
  })
  return {
    name: cleanSheetName('类目配置', used),
    rows: rows,
    cols: []
  }
}

// 纯函数：按行程拼出所有 Sheet，便于测试
function buildSheets(trips, expensesAll, categories) {
  const used = []
  const sheets = trips.map(function (trip) {
    const expenses = expensesAll
      .filter(function (item) { return item.tripId === trip.id })
      .sort(function (a, b) {
        if (a.date < b.date) return 1
        if (a.date > b.date) return -1
        return b.createdAt - a.createdAt
      })
    return tripSheet(trip, expenses, categories, cleanSheetName(trip.name, used))
  })
  sheets.push(categorySheet(categories, used))
  return sheets
}

function buildTripSheets(trip, expenses, categories) {
  const used = []
  return [tripSheet(trip, expenses, categories, cleanSheetName(trip.name, used))]
}

function writeAndShare(fileName, sheets) {
  let bytes
  try {
    bytes = xlsx.buildWorkbook(sheets, new Date())
  } catch (e) {
    notify('生成表格失败')
    return
  }
  const data = new Uint8Array(bytes)
  if (root.AndroidToolbox && typeof root.AndroidToolbox.shareFile === 'function') {
    root.AndroidToolbox.shareFile(
      fileName,
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      bytesToBase64(data)
    )
    return
  }
  const blob = new Blob([data], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

function bytesToBase64(data) {
  let binary = ''
  const chunk = 0x8000
  for (let i = 0; i < data.length; i += chunk) {
    binary += String.fromCharCode.apply(null, data.subarray(i, i + chunk))
  }
  return btoa(binary)
}

function notify(message) {
  if (root.TravelExpenseUi && typeof root.TravelExpenseUi.toast === 'function') {
    root.TravelExpenseUi.toast(message)
  }
}

function exportAllTrips() {
  const trips = store.getTrips()
  if (trips.length === 0) {
    notify('暂无数据可导出')
    return
  }
  const fileName = '出差报销随手记-所有行程-' + stampText() + '.xlsx'
  writeAndShare(fileName, buildSheets(trips, store.getAllExpenses(), store.getCategories()))
}

function exportTrip(tripId) {
  const trip = store.getTrip(tripId)
  if (!trip) {
    notify('行程不存在')
    return
  }
  const fileName = '出差报销随手记-' + cleanFileName(trip.name) + '-' + stampText() + '.xlsx'
  writeAndShare(fileName, buildTripSheets(trip, store.getExpenses(trip.id), store.getCategories()))
}

root.TravelExpenseExcel = {
  cleanFileName,
  cleanSheetName,
  stampText,
  buildSheets,
  buildTripSheets,
  exportAllTrips,
  exportTrip,
  STYLE_BOLD,
  STYLE_MONEY
}
})(window)
