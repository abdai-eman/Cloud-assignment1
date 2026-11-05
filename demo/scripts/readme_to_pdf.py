"""Create README.pdf from README.md using ReportLab: python scripts/readme_to_pdf.py."""
from pathlib import Path
import html
import re
import textwrap
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import mm
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Preformatted, Table, TableStyle, PageBreak

ROOT = Path(__file__).resolve().parents[1]
styles = getSampleStyleSheet()
styles['Normal'].fontSize = 9
styles['Normal'].leading = 13
styles['Normal'].spaceAfter = 6
styles['Normal'].splitLongWords = True
styles.add(ParagraphStyle(name='Cell', parent=styles['Normal'], fontSize=8, leading=11, spaceAfter=0))
styles.add(ParagraphStyle(name='CodeBlock', fontName='Courier', fontSize=7, leading=10,
                         spaceAfter=8, backColor=colors.HexColor('#f1f4f7'), leftIndent=5))

def inline(value):
    value = html.escape(value)
    value = re.sub(r'\*\*(.*?)\*\*', r'<b>\1</b>', value)
    value = re.sub(r'`([^`]+)`', r'<font name="Courier">\1</font>', value)
    return value

def footer(canvas, doc):
    canvas.setStrokeColor(colors.HexColor('#ccd5df'))
    canvas.line(18*mm, 16*mm, A4[0]-18*mm, 16*mm)
    canvas.setFont('Helvetica', 8)
    canvas.setFillColor(colors.HexColor('#536171'))
    canvas.drawString(18*mm, 11*mm, 'Country Lookup | Assignment documentation')
    canvas.drawRightString(A4[0]-18*mm, 11*mm, str(doc.page))

lines = (ROOT / 'README.md').read_text(encoding='utf-8').splitlines()
flow = []
i = 0
width = A4[0] - 36*mm
while i < len(lines):
    line = lines[i]
    if not line.strip():
        i += 1
        continue
    if line.startswith('```'):
        i += 1
        code = []
        while i < len(lines) and not lines[i].startswith('```'):
            code.extend(textwrap.wrap(lines[i], width=106, replace_whitespace=False,
                                      drop_whitespace=False) or [''])
            i += 1
        flow.append(Preformatted('\n'.join(code), styles['CodeBlock']))
        i += 1
        continue
    if line.startswith('|'):
        rows = []
        while i < len(lines) and lines[i].startswith('|'):
            cells = [cell.strip() for cell in lines[i].strip('|').split('|')]
            if not all(re.fullmatch(r':?-+:?', cell.replace(' ', '')) for cell in cells):
                rows.append([Paragraph(inline(cell), styles['Cell']) for cell in cells])
            i += 1
        count = len(rows[0])
        proportions = {2: [0.32, 0.68], 3: [0.17, 0.32, 0.51]}.get(count, [1/count]*count)
        table = Table(rows, colWidths=[width*x for x in proportions], repeatRows=1, hAlign='LEFT')
        table.setStyle(TableStyle([
            ('BACKGROUND', (0,0), (-1,0), colors.HexColor('#dce8f4')),
            ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, colors.HexColor('#f6f8fa')]),
            ('GRID', (0,0), (-1,-1), 0.4, colors.HexColor('#ccd5df')),
            ('VALIGN', (0,0), (-1,-1), 'TOP'),
            ('LEFTPADDING', (0,0), (-1,-1), 6), ('RIGHTPADDING', (0,0), (-1,-1), 6),
            ('TOPPADDING', (0,0), (-1,-1), 6), ('BOTTOMPADDING', (0,0), (-1,-1), 6)
        ]))
        flow.extend([table, Spacer(1, 8)])
        continue
    if line.startswith('#'):
        if line == '## Source layout':
            flow.append(PageBreak())
        level = len(line) - len(line.lstrip('#'))
        style = styles['Title'] if level == 1 else styles['Heading' + str(min(level-1, 3))]
        flow.append(Paragraph(inline(line.lstrip('#').strip()), style))
    else:
        # Keep each Markdown list item as a separate paragraph.
        content = line
        while (i+1 < len(lines) and lines[i+1].strip()
               and not re.match(r'^(#|\||```|- |\d+\. )', lines[i+1])
               and not re.match(r'^(- |\d+\. )', line)):
            i += 1
            content += ' ' + lines[i]
        if content.startswith('- '):
            content = '&#8226; ' + inline(content[2:])
        else:
            content = inline(content)
        flow.append(Paragraph(content, styles['Normal']))
    i += 1

SimpleDocTemplate(str(ROOT/'README.pdf'), pagesize=A4, leftMargin=18*mm, rightMargin=18*mm,
                  topMargin=17*mm, bottomMargin=22*mm,
                  title='Country Lookup - Assignment README', author='').build(
                      flow, onFirstPage=footer, onLaterPages=footer)
print('Created README.pdf')
