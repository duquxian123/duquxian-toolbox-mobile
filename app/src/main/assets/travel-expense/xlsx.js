(function (root) {
// 手写最小 xlsx 生成器：ZIP(store 不压缩) + SpreadsheetML。
// 只支持本产品需要的几件事：多 Sheet、内联字符串、数字、粗体、两位小数、隐藏列。

const CRC_TABLE = (function buildTable() {
  const table = new Array(256)
  for (let n = 0; n < 256; n++) {
    let c = n
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1
    }
    table[n] = c >>> 0
  }
  return table
})()

function crc32(bytes) {
  let crc = 0xFFFFFFFF
  for (let i = 0; i < bytes.length; i++) {
    crc = CRC_TABLE[(crc ^ bytes[i]) & 0xFF] ^ (crc >>> 8)
  }
  return (crc ^ 0xFFFFFFFF) >>> 0
}

function utf8Bytes(text) {
  const out = []
  for (let i = 0; i < text.length; i++) {
    let code = text.charCodeAt(i)
    if (code >= 0xD800 && code <= 0xDBFF && i + 1 < text.length) {
      const low = text.charCodeAt(i + 1)
      if (low >= 0xDC00 && low <= 0xDFFF) {
        code = ((code - 0xD800) << 10) + (low - 0xDC00) + 0x10000
        i++
      }
    }
    if (code < 0x80) {
      out.push(code)
    } else if (code < 0x800) {
      out.push(0xC0 | (code >> 6), 0x80 | (code & 0x3F))
    } else if (code < 0x10000) {
      out.push(0xE0 | (code >> 12), 0x80 | ((code >> 6) & 0x3F), 0x80 | (code & 0x3F))
    } else {
      out.push(
        0xF0 | (code >> 18),
        0x80 | ((code >> 12) & 0x3F),
        0x80 | ((code >> 6) & 0x3F),
        0x80 | (code & 0x3F)
      )
    }
  }
  return new Uint8Array(out)
}

function dosTime(date) {
  const d = date || new Date()
  const year = Math.max(1980, d.getFullYear())
  const time = (d.getHours() << 11) | (d.getMinutes() << 5) | Math.floor(d.getSeconds() / 2)
  const day = ((year - 1980) << 9) | ((d.getMonth() + 1) << 5) | d.getDate()
  return { time: time, date: day }
}

function zip(files, now) {
  const stamp = dosTime(now)
  const localParts = []
  const centralParts = []
  let offset = 0

  files.forEach(function (file) {
    const nameBytes = utf8Bytes(file.name)
    const data = file.data
    const crc = crc32(data)
    const header = new Uint8Array(30 + nameBytes.length)
    header[0] = 0x50; header[1] = 0x4B; header[2] = 0x03; header[3] = 0x04
    header[4] = 20; header[5] = 0
    header[6] = 0x00; header[7] = 0x08 // UTF-8 名称
    header[8] = 0; header[9] = 0 // store
    header[10] = stamp.time & 0xFF; header[11] = (stamp.time >> 8) & 0xFF
    header[12] = stamp.date & 0xFF; header[13] = (stamp.date >> 8) & 0xFF
    header[14] = crc & 0xFF; header[15] = (crc >> 8) & 0xFF
    header[16] = (crc >> 16) & 0xFF; header[17] = (crc >> 24) & 0xFF
    header[18] = data.length & 0xFF; header[19] = (data.length >> 8) & 0xFF
    header[20] = (data.length >> 16) & 0xFF; header[21] = (data.length >> 24) & 0xFF
    header[22] = data.length & 0xFF; header[23] = (data.length >> 8) & 0xFF
    header[24] = (data.length >> 16) & 0xFF; header[25] = (data.length >> 24) & 0xFF
    header[26] = nameBytes.length & 0xFF; header[27] = (nameBytes.length >> 8) & 0xFF
    header.set(nameBytes, 30)

    const central = new Uint8Array(46 + nameBytes.length)
    central[0] = 0x50; central[1] = 0x4B; central[2] = 0x01; central[3] = 0x02
    central[4] = 20; central[5] = 0
    central[6] = 20; central[7] = 0
    central[8] = 0x00; central[9] = 0x08
    central[10] = 0; central[11] = 0
    central[12] = stamp.time & 0xFF; central[13] = (stamp.time >> 8) & 0xFF
    central[14] = stamp.date & 0xFF; central[15] = (stamp.date >> 8) & 0xFF
    central[16] = crc & 0xFF; central[17] = (crc >> 8) & 0xFF
    central[18] = (crc >> 16) & 0xFF; central[19] = (crc >> 24) & 0xFF
    central[20] = data.length & 0xFF; central[21] = (data.length >> 8) & 0xFF
    central[22] = (data.length >> 16) & 0xFF; central[23] = (data.length >> 24) & 0xFF
    central[24] = data.length & 0xFF; central[25] = (data.length >> 8) & 0xFF
    central[26] = (data.length >> 16) & 0xFF; central[27] = (data.length >> 24) & 0xFF
    central[28] = nameBytes.length & 0xFF; central[29] = (nameBytes.length >> 8) & 0xFF
    central[32] = 0
    central[33] = 0
    central[34] = 0
    central[35] = 0
    central[38] = 0
    central[39] = 0
    central[40] = 0
    central[41] = 0
    central[42] = offset & 0xFF; central[43] = (offset >> 8) & 0xFF
    central[44] = (offset >> 16) & 0xFF; central[45] = (offset >> 24) & 0xFF
    central.set(nameBytes, 46)

    localParts.push(header, data)
    centralParts.push(central)
    offset += header.length + data.length
  })

  const centralSize = centralParts.reduce((sum, part) => sum + part.length, 0)
  const end = new Uint8Array(22)
  end[0] = 0x50; end[1] = 0x4B; end[2] = 0x05; end[3] = 0x06
  end[8] = files.length & 0xFF; end[9] = (files.length >> 8) & 0xFF
  end[10] = files.length & 0xFF; end[11] = (files.length >> 8) & 0xFF
  end[12] = centralSize & 0xFF; end[13] = (centralSize >> 8) & 0xFF
  end[14] = (centralSize >> 16) & 0xFF; end[15] = (centralSize >> 24) & 0xFF
  end[16] = offset & 0xFF; end[17] = (offset >> 8) & 0xFF
  end[18] = (offset >> 16) & 0xFF; end[19] = (offset >> 24) & 0xFF

  const all = localParts.concat(centralParts, [end])
  const total = all.reduce((sum, part) => sum + part.length, 0)
  const out = new Uint8Array(total)
  let cursor = 0
  all.forEach(function (part) {
    out.set(part, cursor)
    cursor += part.length
  })
  return out
}

function escapeXml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/\r/g, '')
}

function colLetter(index) {
  let value = index + 1
  let letters = ''
  while (value > 0) {
    const remainder = (value - 1) % 26
    letters = String.fromCharCode(65 + remainder) + letters
    value = Math.floor((value - 1) / 26)
  }
  return letters
}

function cellXml(ref, cell) {
  if (cell == null) return ''
  if (typeof cell === 'number') {
    return '<c r="' + ref + '"><v>' + cell + '</v></c>'
  }
  if (typeof cell === 'string') {
    return '<c r="' + ref + '" t="inlineStr"><is><t xml:space="preserve">' +
      escapeXml(cell) + '</t></is></c>'
  }
  if (cell.number != null) {
    const style = cell.style != null ? ' s="' + cell.style + '"' : ''
    return '<c r="' + ref + '"' + style + '><v>' + cell.number + '</v></c>'
  }
  const style = cell.style != null ? ' s="' + cell.style + '"' : ''
  return '<c r="' + ref + '" t="inlineStr"' + style + '><is><t xml:space="preserve">' +
    escapeXml(cell.text) + '</t></is></c>'
}

function sheetXml(sheet) {
  const cols = (sheet.cols || []).map(function (col) {
    const flags = col.hidden ? ' hidden="1"' : ''
    const width = col.width || 12
    return '<col min="' + col.min + '" max="' + col.max + '"' + flags +
      ' width="' + width + '" customWidth="1"/>'
  }).join('')

  const rows = (sheet.rows || []).map(function (row, rowIndex) {
    const cells = (row || []).map(function (cell, colIndex) {
      return cellXml(colLetter(colIndex) + (rowIndex + 1), cell)
    }).join('')
    if (!cells) return '<row r="' + (rowIndex + 1) + '"/>'
    return '<row r="' + (rowIndex + 1) + '">' + cells + '</row>'
  }).join('')

  return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">' +
    (cols ? '<cols>' + cols + '</cols>' : '') +
    '<sheetData>' + rows + '</sheetData>' +
    '</worksheet>'
}

const STYLES_XML = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
  '<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">' +
  '<numFmts count="1"><numFmt numFmtId="164" formatCode="0.00"/></numFmts>' +
  '<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font>' +
  '<font><b/><sz val="11"/><name val="Calibri"/></font></fonts>' +
  '<fills count="2"><fill><patternFill patternType="none"/></fill>' +
  '<fill><patternFill patternType="gray125"/></fill></fills>' +
  '<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>' +
  '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>' +
  '<cellXfs count="3">' +
  '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>' +
  '<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>' +
  '<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>' +
  '</cellXfs>' +
  '<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>' +
  '</styleSheet>'

const CONTENT_TYPES_HEAD = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
  '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">' +
  '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>' +
  '<Default Extension="xml" ContentType="application/xml"/>' +
  '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'

const ROOT_RELS = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
  '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
  '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>' +
  '</Relationships>'

function buildWorkbook(sheets, now) {
  const sheetFiles = sheets.map(function (sheet, index) {
    return {
      name: 'xl/worksheets/sheet' + (index + 1) + '.xml',
      data: utf8Bytes(sheetXml(sheet))
    }
  })

  const workbookSheets = sheets.map(function (sheet, index) {
    return '<sheet name="' + escapeXml(sheet.name) + '" sheetId="' + (index + 1) +
      '" r:id="rId' + (index + 1) + '"/>'
  }).join('')

  const workbook = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" ' +
    'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">' +
    '<sheets>' + workbookSheets + '</sheets></workbook>'

  const rels = sheets.map(function (sheet, index) {
    return '<Relationship Id="rId' + (index + 1) +
      '" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet' +
      (index + 1) + '.xml"/>'
  }).join('') +
    '<Relationship Id="rId' + (sheets.length + 1) +
    '" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>'

  const workbookRels = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
    rels + '</Relationships>'

  const overrides = sheets.map(function (sheet, index) {
    return '<Override PartName="/xl/worksheets/sheet' + (index + 1) +
      '.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
  }).join('') +
    '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>' +
    '</Types>'

  const files = [
    { name: '[Content_Types].xml', data: utf8Bytes(CONTENT_TYPES_HEAD + overrides) },
    { name: '_rels/.rels', data: utf8Bytes(ROOT_RELS) },
    { name: 'xl/workbook.xml', data: utf8Bytes(workbook) },
    { name: 'xl/_rels/workbook.xml.rels', data: utf8Bytes(workbookRels) },
    { name: 'xl/styles.xml', data: utf8Bytes(STYLES_XML) }
  ].concat(sheetFiles)

  return zip(files, now)
}

root.TravelExpenseXlsx = {
  buildWorkbook,
  crc32,
  escapeXml,
  colLetter,
  sheetXml
}
})(window)
