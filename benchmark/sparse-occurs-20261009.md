# OCCURS com posições sob demanda

A expansão antecipada de uma tabela de N posições criava N variáveis lógicas por
campo relevante, mesmo com apenas dois acessos. Duas dimensões multiplicavam
esse custo antes de analisar o fluxo. A implementação remove `indexElements` e
usa o mesmo solver com um valor compartilhado para as posições restantes e
posições específicas somente quando precisam ser distinguidas.

Índices literais estabelecem a demanda inicial. Índices resolvidos por dados,
expressões ou instanciação de PERFORM ampliam a demanda pelo solver canônico.
A análise fecha essa demanda antes de publicar respostas: uma posição nova
requer reconstruir as projeções e caches com o novo esquema. Não há solver de
fallback, regra por tamanho ou caminho especial por fixture. A demanda cresce
monotonicamente e o orçamento limita as visitas de fluxo acumuladas entre passagens.

O valor compartilhado representa apenas posições sem célula específica. Leituras
com índice desconhecido unem esse valor às células; quando todas as posições são
específicas, o valor compartilhado deixa de participar do resumo. Escritas
conhecidas substituem somente a posição; escritas incertas conservam candidatos
e remainder. VALUE, INITIALIZE, grupos, escrita parcial e aliases participam da
mesma representação. A reconstrução de texto usa o default nas posições ausentes,
sem misturar os candidatos de outras posições.

## Evidência

A matriz adversarial final tem 23 fontes e oráculos exatos de candidatos e
remainder. A rodada inicial de 21 fontes × 3 foi executada antes da alteração
de produção. Duas fontes adicionais e o ajuste da fixture aritmética foram
medidos usando o mesmo JAR anterior, fixado a partir de main
`f12ccfd80ea4dc4ecf07967bd9076ae2b54881ee`, hash
`fae9d87745f415c54dd5815b0bced41fabcac6e498944f01c7e110731eadedfb`.
Os números finais, hashes, comandos normalizados e contadores estão no JSON
correspondente. Logs e outputs brutos permanecem em
`benchmark/results/sparse-occurs-20261009/`, ignorado pelo Git.

Todas as JVMs de análise rodam sequencialmente, JDK 21, heap 512 MiB, RSS
supervisionado em 640 MiB e timeout de 45 s. A curva principal usa orçamento
10 mil e três repetições. A rodada adicional usa o orçamento padrão um milhão,
sem aumentar o heap; não se confunde falha por limite artificial com OOM.

| Caso | Valores rastreados antes → depois | Dados, mediana ms antes → depois | RSS máximo MiB antes → depois |
| --- | ---: | ---: | ---: |
| sparse-100 | 101 → 4 | 92.05 → 47.50 | 153.1 → 123.9 |
| sparse-1000 | 1001 → 4 | 631.85 → 48.30 | 204.6 → 123.7 |
| nested-50 | 2501 → 4 | 1841.11 → 44.02 | 223.4 → 122.8 |
| alias-write | 2002 → 8 | 1579.46 → 50.29 | 242.2 → 137.0 |
| index-through-perform | 1002 → 5 | 859.88 → 55.64 | 383.8 → 139.1 |
| literal-loop | 1 → 1 | 43.97 → 45.34 | 131.5 → 136.6 |

O candidato concluiu **69/69 execuções** (23 fontes × 3). Nos 60 pares que a
baseline concluía, o JSON foi byte a byte idêntico, assim como diagnósticos e
exit codes; os hashes das fontes coincidem. Nas outras nove execuções, a baseline
atingiu RESOURCE_LIMIT: sparse-5000 no limite de cálculos paramétricos,
sparse-50000 na dimensão e nested-200 nos elementos. O candidato concluiu esses
mesmos casos com o orçamento 10 mil. O máximo RSS da matriz final foi 143,8 MiB.

As curvas sparse e nested materializam duas posições, independentemente da
extensão; aliases equivalentes usam quatro células nominais. CALL literal no
loop não demanda posições da tabela. O caso index-through-perform exige duas
passagens de demanda; seu tempo inclui ambas. Não há ganho universal: o controle
literal-loop passou de 501,96 para 504,08 ms totais (medianas), praticamente estável.

Com o orçamento padrão de um milhão, em uma execução por caso, sparse-5000
passou de 11,43 s para 0,76 s de parede. sparse-50000 e nested-200 atingiram o
timeout de 45 s na baseline; o candidato concluiu em 0,76 s e 0,70 s.
Nenhuma JVM de análise aumentou o heap; nenhum desses encerramentos foi OOM.

Qualificação nova contra o código final:

- **208 testes focados/de regressão** e **1.076 FAST**, sem falhas ou skips.
- **CardDemo 73/73**, 815 relações, zero perdas/acréscimos e JSON, diagnósticos e
  exits exatos contra a qualificação de main: 26 PASS e os mesmos 47 PARTIAL.
  Pico RSS 395,8 MiB; 117,35 s de parede somados, uma execução por fonte.
- FIXTURE02 reconstruída e variante de 400 chamadas executadas contra ambos os
  JARs, com JSON e diagnósticos exatos. A original conserva zero dependências;
  a variante conserva 400 destinos.
- JAR standalone executado em diretório e ambiente isolados, heap 256 MiB.

O JAR final medido tem SHA-256 `b7cc0654207091e1872071a7a3267547daa170ce9b1fb1026702c99e69fa5ab1`.
O build e os hashes de produção estão no JSON; parse, binding e CFG continuam
na mesma arquitetura, sem novos produtos intermediários.

Reprodução: gere as fontes com `python3 -B benchmark/generate-sparse-occurs.py`;
rode `python3 -B benchmark/run-sparse-occurs.py OUTPUT --jar JAR --repeats 3`.
Use JDK 21 em PATH para repetir a curva registrada. O runner mantém heap e RSS
fixos, guarda inputs, JSON, métricas e logs, e recusa sobrescrever uma rodada.
Para os três casos adicionais use `--case sparse-5000 --case sparse-50000
--case nested-200 --max-work 1000000 --repeats 1`.


## Limites e achados preservados

Compartilhar posições não elimina o custo de informação realmente distinta:
se K posições relevantes precisam ser diferenciadas, a representação ainda pode
crescer com K. Reconstruir textos de grupos pode exigir trabalho proporcional ao
texto. Índices descobertos em cadeia podem exigir múltiplas passagens do mesmo
solver; os contadores `tableDemandPasses` e `materializedTableElements` tornam
isso observável. Extensão não estabelecida permanece aberta.

A primeira rodada exploratória encontrou dois oráculos mal especificados:
COMPUTE é opaco no produto atual, e índice desconhecido mantém remainder mesmo
quando todos os candidatos conhecidos estão estabelecidos. As expectativas
foram explicitadas na matriz antes da baseline principal; a rodada original
não foi alterada.

O primeiro FAST detectou divergência sintática preexistente entre os parsers
para adição no subscrito, sem alteração de código do frontend. A entrada original
foi preservada; a fixture final usa multiplicação e foi novamente medida com o
JAR da baseline. O contrato de equivalência entre parsers permaneceu exigido.
Um teste adicional expôs perda de um candidato na versão intermediária ao
reconstruir um grupo após INITIALIZE e uma escrita indexada; o RED e o GREEN
foram preservados, e a reconstrução passou a usar o valor das posições restantes.
