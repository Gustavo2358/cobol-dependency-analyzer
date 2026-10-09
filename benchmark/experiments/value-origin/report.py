#!/usr/bin/env python3
"""Derive the experiment report from preserved raw evidence; never rewrite oracles."""
import argparse,collections,hashlib,json,re,statistics,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
HERE=Path(__file__).resolve().parent

def sha(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  for b in iter(lambda:f.read(262144),b''):h.update(b)
 return h.hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('evidence',type=Path);a=p.parse_args();e=a.evidence.resolve()
 read=lambda name:json.loads((e/name).read_text())
 overlay=read('overlay-v10/manifest.json');focused=[r for r in read('focused-v10/results.json') if r['variant']=='origin'];adversarial=[r for r in read('adversarial-v10/results.json') if r['variant']=='origin'];card=read('carddemo-v10/summary.json');scale=read('scale-v10/results.json');paired=read('paired-v9/results.json')
 assert len(focused)==37 and len(adversarial)==4 and card['sources']==73
 for r in focused+adversarial:assert r['completed'] and r['comparable'] and not r['missing'],r['case']
 assert card['completed']==73 and card['missing']==0 and card['inputHashesUnchanged']==185
 sourceMatches={name:sha(HERE/name)==overlay[field] for name,field in [('ValueOriginExperiment.java','solverSha256'),('ValueOriginPublication.java','publicationSha256')]};assert all(sourceMatches.values())
 scales=[]
 for r in scale:
  assert r['completed'] and not r['missingOraclePrograms'] and not r['additionalOraclePrograms']
  case=r['case'];n=int(case.split('-')[-1]);folder=e/'scale-v10'/case/'run1/origin';lines=(folder/'stderr.log').read_text().splitlines();pub=next(s for s in lines if s.startswith('ORIGIN_PUBLICATION '));counts={k:int(v) for k,v in re.findall(r'(\w+)=(\d+)',pub)};metrics=json.loads((folder/'metrics.jsonl').read_text().splitlines()[0])['metrics'];probe=r['origin'][0]
  if case.startswith('dynamic-'):
   assert probe['originNodes']==64*n+22 and probe['candidateSlots']==12*n+2 and probe['work']==540*n+146
   assert counts['enumeratedPairs']==4*n+1 and counts['naivePairs']==2*n*n+3*n
  scales.append({'case':case,'n':n,'seconds':r['seconds'],'peakRssMiB':r['peakRssKiB']/1024,'programs':len(r['dependencies']),'origin':probe,'publication':counts,'phaseSeconds':{k:round(v/1e9,6) for k,v in metrics.items() if k.endswith('Nanos')},'oracleMissing':r['missingOraclePrograms'],'oracleAdditional':r['additionalOraclePrograms'],'inputLoc':len((e/'scale-v10/inputs'/f'{case}.cbl').read_text().splitlines()),'comparableToBaseline':False})
 pairs={}
 for case in ['dynamic-64','fanout-128']:
  rows=[r for r in paired if r['case']==case];assert len(rows)==6
  assert all(r['completed'] for r in rows) and all(r['fullJsonEqual'] for r in rows if r['variant']=='origin')
  pairs[case]={variant:{'medianSeconds':statistics.median(r['seconds'] for r in rows if r['variant']==variant),'medianRssMiB':statistics.median(r['peakRssKiB']/1024 for r in rows if r['variant']==variant)} for variant in ['baseline','origin']}
  pairs[case]['prototypeVersion']='v9';pairs[case]['repeats']=3;pairs[case]['fullJsonEqual']=True
 manifest={str(path.relative_to(e)):sha(path) for path in sorted(e.rglob('*')) if path.is_file() and path.name!='raw-sha256.json'}
 (e/'raw-sha256.json').write_text(json.dumps(manifest,indent=2)+'\n')
 prodDiff=subprocess.check_output(['git','-C',str(ROOT),'diff',overlay['baselineCommit'],'--','src','pom.xml'],text=True);assert not prodDiff
 summary={'baselineCommit':overlay['baselineCommit'],'jarSha256':overlay['jarSha256'],'branch':'experiment/value-origin-graph','prototypeVersion':'v10','sourceHashesMatchMeasuredOverlay':sourceMatches,'productionSourcesUnchanged':True,'productionQualified':False,'heapMiB':512,'rssGuardMiB':768,'hostReserveMiB':2048,'maxWork':100000000,'focused':{'cases':len(focused),'missing':sum(len(r['missing']) for r in focused),'additional':sum(len(r['additional']) for r in focused),'fullJsonEqual':sum(r['fullJsonEqual'] for r in focused),'verdicts':dict(collections.Counter(r['verdict'] for r in focused))},'adversarial':{'cases':len(adversarial),'missing':sum(len(r['missing']) for r in adversarial),'additional':sum(len(r['additional']) for r in adversarial),'fullJsonEqual':sum(r['fullJsonEqual'] for r in adversarial)},'carddemo':card,'pairedV9':pairs,'scaleV10':scales,'baselineDynamic128':{'version':'paired-v3','repeats':3,'timeoutSeconds':60,'allTimedOut':all(r['guard']=='TIME_GUARD' for r in read('paired-v3/results.json') if r['case']=='dynamic-128' and r['variant']=='baseline')},'rawEvidenceFiles':len(manifest),'rawManifestSha256':sha(e/'raw-sha256.json'),'rawEvidenceDirectory':str(e)}
 (HERE/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
 table='\n'.join(f"| {r['n']} | {r['seconds']:.2f} | {r['peakRssMiB']:.0f} | {r['origin']['originNodes']:,} | {r['origin']['candidateSlots']:,} | {r['publication']['enumeratedPairs']:,} |" for r in scales if r['case'].startswith('dynamic-'))
 large=next(r for r in scales if r['case']=='dynamic-2048');d=pairs['dynamic-64'];f=pairs['fanout-128'];cf=summary['focused']
 (HERE/'RESULTS.md').write_text(f'''# Resultado do experimento de origens — 2026-10-09

A direção é promissora: o protótipo elimina a enumeração por contexto na família
medida, preserva os candidatos do corpus e termina N=2.048 com heap de 512 MiB.
É investigação, **não uma implementação qualificada para produção**. Predicados,
retornos não provados e posições de tabelas são aproximados; há nomes adicionais.
Não existe prova de preservação para COBOL arbitrário nem promessa de custo linear
universal. O programa corporativo não foi executado nesta sessão.

## Baseline e isolamento

- Base limpa: `{overlay['baselineCommit']}` (merge PR 5).
- JAR congelado: `{overlay['jarSha256']}`.
- Branch: `experiment/value-origin-graph`; nenhuma alteração em `src/` ou `pom.xml`.
- Overlay final: v10; hashes do solver e da agregação conferidos com os fontes.
- Reuso da qualificação da produção: FAST 1.093 testes em 142 suites,
  CardDemo 73/815 e 36 fixtures da execução anterior no HEAD remoto.
  FAST **não foi reexecutado contra o overlay**; não se usa esse reuso para
  qualificar a nova semântica. O overlay tem testes diferenciais próprios.
- Todas as análises sequenciais: Xmx512 MiB, RSS768 MiB, reserva host2 GiB,
  max-work100M, timeout60 s. Compilador128 MiB. Nenhum aumento de heap.

## O mecanismo testado

```text
consulta de uma variável
       |
       v
origens antes da consulta <--- definições/escritas que podem alcançá-la
       |                          |
       |                          +-- constante: encerra a busca anterior
       |                          +-- abertura: origens próprias + UNKNOWN
       |                          +-- transformação: operandos necessários
       v
ciclos de união/cópia condensados em um componente compartilhado
       |
       v
candidatos compartilhados ---> uma enumeração por conjunto/tipo ---> JSON
```

O frontend e as operações locais de valores são reaproveitados. O alcance físico
admite todos os ramos publicados e retomadas especulativas de chamadas. Não há
contextos de invocação, decisão por estado ou solver antigo como fallback. Só os
ciclos de união/cópia são condensados; transformações não são equacionadas com
identidade. BEFORE/AFTER preserva a ordem das escritas e das consultas.

O suporte da saída é essencial: inicializar ou abrir vários campos não torna o
valor antigo de cada destino um operando de todos os outros. Avaliação com TOP
certifica saídas constantes; o suporte local de abertura permite uma equação de
união. Nos demais casos, a operação é compartilhada por posição e operandos.
Textos redefinidos precisam também dos campos irmãos usados na reconstrução.
Essa última dependência é real e foi mantida após um contraexemplo.

Na agregação, consultas com o mesmo conjunto imutável compartilham a enumeração.
A separação por tipo/regra de validação mantém programas e arquivos distintos.
Como cada consulta do grupo tem os mesmos nomes, escolher sua menor proveniência
antes de enumerar preserva a menor proveniência por nome; avisos inválidos CICS
continuam emitidos para cada linha. Só se compartilha identidade efetivamente
igual, sem supor que conjuntos diferentes sejam equivalentes.

## Comparação de desempenho

Três repetições pareadas com o checkpoint v9; todas com JSON idêntico:

| Família | Baseline mediana | Protótipo v9 mediana | RSS mediano antes → depois |
|---|---:|---:|---:|
| Dinâmica N=64 | {d['baseline']['medianSeconds']:.2f} s | {d['origin']['medianSeconds']:.2f} s | {d['baseline']['medianRssMiB']:.0f} → {d['origin']['medianRssMiB']:.0f} MiB |
| Literal N=128 | {f['baseline']['medianSeconds']:.2f} s | {f['origin']['medianSeconds']:.2f} s | {f['baseline']['medianRssMiB']:.0f} → {f['origin']['medianRssMiB']:.0f} MiB |

Baseline dinâmica N=128: três execuções no checkpoint anterior atingiram a guarda
de 60 s. Esses resultados foram preservados, sem repetir a falha ou aumentar heap.
Portanto, N maiores usam o oráculo sintético de nomes, **não paridade** com uma
baseline que não terminou. V10 corrige operandos de aliases; a família sintética
não tem REDEFINES, e os contadores continuam os mesmos. Seus tempos abaixo são
novas medidas individuais, não medianas nem projeções de speedup.

| N dinâmico | Tempo v10 (s) | RSS (MiB) | Origens | Candidatos em valores distintos | Pares enumerados |
|---:|---:|---:|---:|---:|---:|
{table}

Todas as escalas retornaram os N+1 nomes esperados, sem nomes adicionais ao
oráculo. N=2.048 tem {large['inputLoc']:,} linhas e 8.192 consultas. O trabalho
registrado é `540N+146`, origens `64N+22`, candidatos em valores distintos
`12N+2` e enumerações da saída `4N+1`. Essas relações são observadas/checadas
nesta família. Para N=2.048, a agregação evita percorrer
{large['publication']['naivePairs']:,} pares e percorre
{large['publication']['enumeratedPairs']:,}. A fase de resolução/agregação final
levou {large['phaseSeconds']['resolutionNanos']:.3f} s.

Os contadores de controle canônico no JSON de métricas são zero porque esse
solver não roda. A topologia real do protótipo é medida por ORIGIN_PROBE.
`evaluations` conta transformações no ponto fixo; avaliações de independência
são contadas separadamente em ORIGIN_PRUNING.

## Dependências e precisão

- V10: {cf['cases']} fixtures diferenciais completas, zero candidatos perdidos,
  {cf['additional']} adicionais; {cf['fullJsonEqual']} JSONs idênticos.
- Quatro fixtures novas: grupos parciais, alias com duas consultas, alias com uma
  consulta e publicação programa/arquivo. Todas com JSON idêntico à baseline.
- CardDemo v10: 73/73 completos, 815 candidatos preservados, zero perdidos,
  {card['additional']} adicionais; 62 JSONs idênticos e 11 com um nome adicional.
  Os 185 hashes de entrada permaneceram iguais. Pico RSS {card['peakRssMiB']:.0f} MiB.
- Diagnostics diferem deliberadamente: o protótipo sempre informa aproximação e
  pode ampliar remainders desconhecidos. Exit1/PARTIAL não é paridade operacional.
- Fixture02 retorna 50 nomes adicionais: a baseline não admite a retomada de
  PERFORMs que não retornam; o experimento admite. Não chamar isso de precisão.

Não se alteraram oráculos para produzir PASS. Os nomes extras incluem retornos
admitidos sem prova, correlação ignorada e elementos de tabelas resumidos. Os
11 extras do CardDemo são registrados caso a caso em `carddemo-v10/results.json`.
A política de aceitar possíveis dependências adicionais precisa fazer parte da
semântica do produto antes de uma substituição do caminho canônico.

## Paredes descobertas e versões descartadas

1. v2 perdeu três nomes de índices conhecidos porque não havia células de tabela.
   v3 passou a ler o resumo da declaração; pode incluir posições adicionais.
2. v4 perdeu SECOND na escrita inteira de tabela. v5 passa a decodificar todos os
   trechos conhecidos no resumo, sem materializar células; FIRST torna-se extra.
3. v5–v7 provocaram OOM no COACTUPC. JFR apontou construção de Origin/Cell/arestas,
   antes do ponto fixo; v7 chegou a mais de 1,9 milhão de origens. Não era concatenação
   de candidatos. v8 restringiu operandos à saída solicitada: cerca de 35 mil origens,
   execução completa e JSON idêntico. O bom resultado sintético sozinho não bastou.
4. v9 eliminou a enumeração repetida consultas×candidatos na agregação.
5. v9 ainda perdeu uma consulta através de REDEFINES. Com duas consultas, o nome
   reaparecia depois, mascarando a perda no conjunto e alterando sua primeira linha.
   A fixture de uma única consulta comprovou MISSING. v10 inclui os irmãos do texto
   redefinido e preserva candidato e proveniência nas duas fixtures.

Uma tentativa `carddemo-v5-profile` usou posição inválida do argumento JFR; o
runner antigo classificou exit1 sem JSON como completo. É evidência inválida,
excluída das conclusões. O runner foi corrigido para exigir JSON. A repetição
`carddemo-v5-profile-corrected` confirmou OOM e gerou o JFR utilizado no diagnóstico.
O primeiro CardDemo v5 foi interrompido após falhas; não conta como corpus completo.

## Decisão e limites

O experimento justifica desenvolver uma análise de possibilidades por origens,
com compartilhamento de ciclos e saídas. Ele remove o multiplicador de contextos
e de pares redundantes na família que motivou a investigação. Não é só compressão
de objetos. O custo pode voltar a crescer com várias variáveis realmente relevantes,
reconstruções de textos e produtos de candidatos: se houver muitos nomes distintos
como resultado real, sua enumeração é inevitável.

Antes de levar ao produto: definir a política conservadora de alcance/retorno,
validar handlers e demais operadores, formalizar suporte por saída e ampliar os
contraexemplos de alias/grupos. Substituir o solver canônico só depois dessa
qualificação, sem manter fallback ou caminhos especiais por fixture.

## Evidência

Evidência bruta local: `{e.relative_to(ROOT)}/`, incluindo comandos, inputs,
outputs, métricas, RSS/CPU/guardas, overlays de cada versão e JFR. Inventário SHA256:
`raw-sha256.json`, {len(manifest)} arquivos, hash `{summary['rawManifestSha256']}`.
Resumo versionado: [summary.json](summary.json). Gerador: [report.py](report.py).
''')
 print(json.dumps({'focused':summary['focused'],'adversarial':summary['adversarial'],'carddemoMissing':card['missing'],'carddemoAdditional':card['additional'],'largestScale':large['seconds'],'rawFiles':len(manifest)},indent=2))
if __name__=='__main__':main()
