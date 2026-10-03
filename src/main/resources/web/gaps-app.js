/* The UI presents the producer's assessment; it does not decide which proof closes a gap. */
(function () {
  'use strict';
  const labels = { SUPERSEDED: 'Superado', PARTIAL: 'Parcialmente coberto', OPEN: 'Aberto' };
  const data = window.SEMANTIC_GAP_ASSESSMENT;
  const el = id => document.getElementById(id);
  const node = (tag, text, className) => {
    const n = document.createElement(tag);
    if (text !== undefined) n.textContent = text;
    if (className) n.className = className;
    return n;
  };
  if (!data) { el('result-count').textContent = 'O arquivo de avaliação não foi carregado.'; return; }
  const fmt = n => n.toLocaleString('pt-BR');
  let page = 0;
  const pageSize = 50;
  for (const [i, unit] of data.units.entries()) {
    const option = node('option', `${unit.unit.canonicalProgramName} · unidade ${i + 1}`);
    option.value = String(i);
    el('unit-filter').append(option);
  }
  function selectedUnits() {
    const value = el('unit-filter').value;
    return value === '' ? data.units : [data.units[Number(value)]];
  }
  function refreshSummary() {
    const value = el('unit-filter').value;
    const scope = value === '' ? data : data.units[Number(value)];
    el('counts').replaceChildren();
    for (const [key, label] of [['raw', 'Registros originais'], ['pending', 'Pendentes nesta avaliação'], ['superseded', 'Superados por prova'], ['partial', 'Parcialmente cobertos'], ['open', 'Abertos']]) {
      const card = node('div', undefined, 'count');
      card.append(node('strong', fmt(scope.counts[key])), node('span', label));
      el('counts').append(card);
    }
    el('ranking').replaceChildren();
    for (const item of scope.ranking) {
      const row = node('tr');
      const first = node('td');
      const button = node('button', item.code);
      button.type = 'button';
      button.addEventListener('click', () => { el('search').value = item.code; el('status-filter').value = 'PENDING'; page = 0; render(); el('details-title').scrollIntoView(); });
      first.append(button); row.append(first);
      for (const key of ['pending', 'partial', 'superseded', 'raw']) row.append(node('td', fmt(item.counts[key])));
      el('ranking').append(row);
    }
  }
  function render() {
    const query = el('search').value.trim().toLowerCase();
    const status = el('status-filter').value;
    const rows = selectedUnits().flatMap(unit => unit.gaps.map(gap => ({ unit, gap }))).filter(({ unit, gap }) => {
      if (status === 'PENDING' ? gap.status === 'SUPERSEDED' : status && gap.status !== status) return false;
      return !query || [unit.unit.canonicalProgramName, gap.code, gap.statement, gap.detail, gap.remaining,
        gap.provenance.original.file, gap.provenance.original.startLine].join(' ').toLowerCase().includes(query);
    });
    const pages = Math.max(1, Math.ceil(rows.length / pageSize));
    page = Math.min(page, pages - 1);
    el('result-count').textContent = `${fmt(rows.length)} registros correspondem aos filtros.`;
    el('page-number').textContent = `${page + 1} / ${pages}`;
    el('previous').disabled = page === 0; el('next').disabled = page + 1 === pages;
    const fragment = document.createDocumentFragment();
    for (const { unit, gap } of rows.slice(page * pageSize, (page + 1) * pageSize)) {
      const detail = node('details'); const summary = node('summary');
      summary.append(node('span', labels[gap.status], `badge ${gap.status}`), node('b', gap.code));
      const origin = gap.provenance.original;
      summary.append(node('small', `${unit.unit.canonicalProgramName} · ${origin.file}:${origin.startLine} · ${gap.statement} · gaps[${gap.gapIndex}]`));
      const list = node('dl');
      const field = (title, value) => { list.append(node('dt', title), node('dd', value)); };
      field('Diagnóstico original', gap.detail);
      field('Escopo / dimensão avaliada', `${gap.scope} / ${gap.dimension}`);
      field('Regra da avaliação', gap.rule);
      field('Provas no Semantic Product desta unidade', gap.evidence.length
        ? gap.evidence.map(e => `${e.pointer}\n${e.authority} · ${e.description}`).join('\n\n') : 'Nenhuma prova substituta admitida.');
      field('Limite da conclusão', gap.remaining);
      field('Proveniência completa', JSON.stringify(gap.provenance, null, 2));
      detail.append(summary, list); fragment.append(detail);
    }
    el('occurrences').replaceChildren(fragment);
  }
  el('unit-filter').addEventListener('change', () => { page = 0; refreshSummary(); render(); });
  el('status-filter').addEventListener('change', () => { page = 0; render(); });
  el('search').addEventListener('input', () => { page = 0; render(); });
  el('previous').addEventListener('click', () => { page--; render(); });
  el('next').addEventListener('click', () => { page++; render(); });
  refreshSummary(); render();
}());
