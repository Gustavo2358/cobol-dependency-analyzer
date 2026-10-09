#!/usr/bin/env python3
"""Plot saved telemetry using ReportLab LinePlot; never rerun the analyzer."""
import argparse, json
from pathlib import Path
from reportlab.graphics.shapes import Drawing, String, Rect, Line
from reportlab.graphics.charts.lineplots import LinePlot
from reportlab.graphics import renderSVG
from reportlab.lib.colors import HexColor, Color

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--input', type=Path, default=Path(__file__).with_name('hub-large-heap-20261009.json'))
p.add_argument('--output', type=Path, default=Path(__file__).with_name('hub-large-heap-20261009.svg'))
a = p.parse_args()
evidence = json.loads(a.input.read_text())
row = evidence['runs']['large-heap-boundary'][0]
samples = row['telemetry']
summary = evidence['telemetrySummaries']['large-heap-boundary/hub-perform-flags-2h-15b']
figure = Drawing(800, 550)
figure.add(String(22, 525, 'RSS estabiliza, mas a CPU continua ativa', fontName='Helvetica-Bold', fontSize=17))
figure.add(String(22, 501, '15 caixinhas por hub; heap de 1 GiB; resultado: OOM aos %.2f s.' % row['seconds'], fontSize=11))
figure.add(String(22, 480, '%d Full GCs; %.2f s de pausas GC somadas.' % (summary['fullGC'], summary['gcPausesSeconds']), fontSize=11))
for bottom, key, limit, step, title, color in [
        (285, 'rssKiB', 1400, 350, 'RSS (MiB)', '#146c94'),
        (65, 'cpuSeconds', 600, 150, 'CPU acumulada (s, todas as threads)', '#ba3d38')]:
    plot = LinePlot()
    plot.x, plot.y, plot.width, plot.height = 80, bottom, 670, 155
    plot.data = [[(s['seconds'], s[key]/1024 if key == 'rssKiB' else s[key]) for s in samples]]
    plot.lines[0].strokeColor = HexColor(color)
    plot.lines[0].strokeWidth = 2
    plot.xValueAxis.valueMin, plot.xValueAxis.valueMax, plot.xValueAxis.valueStep = 0, 84, 20
    plot.yValueAxis.valueMin, plot.yValueAxis.valueMax, plot.yValueAxis.valueStep = 0, limit, step
    plot.xValueAxis.labels.fontSize = plot.yValueAxis.labels.fontSize = 10
    plot.yValueAxis.visibleGrid = True
    plot.yValueAxis.gridStrokeColor = Color(.88, .88, .88)
    figure.add(Rect(80+60/84*670, bottom, (row['seconds']-60)/84*670, 155,
                    fillColor=HexColor('#fff0d7'), strokeColor=None))
    figure.add(plot)
    x = 80+row['seconds']/84*670
    figure.add(Line(x, bottom, x, bottom+155, strokeColor=HexColor('#ba3d38'), strokeDashArray=[4,3]))
    figure.add(String(80, bottom+171, title, fontSize=11))
    figure.add(String(350, bottom-34, 'Tempo de parede (s)', fontSize=11))
figure.add(String(80, 10, 'CPU inclui GC e JIT. RSS não é ocupação do heap. Faixa amarela: 60 s até a falha.', fontSize=10))
renderSVG.drawToFile(figure, str(a.output))
