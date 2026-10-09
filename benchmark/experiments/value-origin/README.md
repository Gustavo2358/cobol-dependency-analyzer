# Experimento de origens de valores

Baseline congelada: `52c1b82dc6accbb615818cf5b5298843a85b0f01` (PR 5 mergeado).
O JAR qualificado e os fontes de produção não são modificados. `build.py`
compila um overlay de investigação a partir desse SHA; não há fallback para o
solver canônico durante a execução do protótipo.

O protótipo constrói alcance físico sem contextos, uma rede esparsa de definições
BEFORE/AFTER, e componentes fortemente conexos somente entre nós de união/cópia.
Transformações continuam separadas e usam as operações locais do produto. As
consultas demandam apenas suas origens, não estados completos por chamador.

Escritas fechadas quando avaliadas com entradas desconhecidas encerram a busca
por origens. Aberturas com suporte conhecido viram união com um fato UNKNOWN.
As demais operações são compartilhadas por posição e conjunto de operandos
consumidos pela saída solicitada; destinos independentes não viram entradas.
Leituras consomem a declaração nominal ou o grupo textual inteiro. A
possibilidade de alias é tratada na escrita, sem expandir toda leitura.

A agregação final enumera cada conjunto imutável compartilhado uma vez por
tipo de dependência e regra de validação, mantendo a proveniência mínima e
todos os avisos de nomes CICS inválidos. Isso evita repetir o produto
consultas × candidatos quando o JSON só exige uma lista deduplicada.

Intervenções semânticas deliberadas: predicados/correlações entre chamadores não
filtram o alcance; retomadas de chamadas são admitidas mesmo sem retorno provado;
as posições de tabelas são resumidas pela declaração. Grupos lógicos usados como
texto preservam um canal com a alternativa inteira. Essas aproximações podem
introduzir nomes adicionais. Não se afirma soundness geral nem paridade.

`run.py` registra candidatos faltantes, adicionais, proveniência, limites de
recursos e oráculos sintéticos. `carddemo.py` compara o overlay com a execução
congelada dos 73 fontes. Exit 0 dos scripts significa que a coleta terminou,
não que a mudança semântica foi aprovada. É necessário JSON publicado para
considerar uma execução completa. Resultados incompletos não são paridade.
CardDemo interrompe no primeiro erro de execução para permitir investigação.
`--diagnostic` remove ExitOnOutOfMemoryError para capturar a falha e grava
JFR com limite de 16 MiB, mantendo o mesmo heap e as guardas de recursos.
Toda análise usa heap 512 MiB, guarda RSS 768 MiB e reserva do host de 2 GiB,
sequencialmente. Não se aumenta o heap para obter conclusão.

Exemplo (JDK 21 existente no PATH/JAVA_HOME):

```sh
python3 benchmark/experiments/value-origin/build.py /tmp/origin-overlay \
  --jar benchmark/results/remote-requalification-20261009/remote.jar
python3 benchmark/experiments/value-origin/run.py /tmp/origin-runs \
  --jar benchmark/results/remote-requalification-20261009/remote.jar \
  --overlay /tmp/origin-overlay \
  --cases caller-correlation full-unknown-write fanout-128 dynamic-64
```

O grafo não promete custo linear universal: o número de variáveis relevantes
pode crescer com o programa; composição de valores pode produzir produtos de
candidatos. A medição deve distinguir tamanho do grafo, conjuntos únicos de
candidatos e custo da resolução, além do tempo/RSS total incluindo o frontend.

Os contadores `ORIGIN_STAGE`, `ORIGIN_PRUNING`, `ORIGIN_PROBE` e
`ORIGIN_PUBLICATION` ficam no stderr bruto. `evaluations` no PROBE conta
transformações durante o ponto fixo; `independentTransfers` no PRUNING conta
as avaliações usadas para certificar independência. `candidateSlots` conta
candidatos sobre valores distintos por identidade, sem repetir compartilhamentos.

O relatório e o resumo verificável da execução ficam em [RESULTS.md](RESULTS.md)
e `summary.json`. A evidência bruta local, incluindo versões descartadas, fica
em `benchmark/results/value-origin-20261009/`, sob o gitignore existente.
