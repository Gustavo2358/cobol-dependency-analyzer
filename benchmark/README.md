# Medições locais

[Migração para `com.imd.cobolexplorer`](namespace-migration-20261008.md):
validação de build, identidade do JAR e preservação dos resultados após a
troca dos pacotes Java e das coordenadas Maven.

[Solver canônico de resumos](solver-unification-20261008.md): um único caminho
para PERFORM, transferências, escapes e handlers, com junção por componentes
fortemente conexos. OR1216 passou de 1.485.530 para 18.040 itens de trabalho;
na comparação nova, de 9.98 s para 4.12 s.
294 testes específicos, 856 FAST, 30/30 ampliados, 20/20 originais e CardDemo
73/73, com 815 relações e saídas/códigos/diagnósticos preservados. O relatório
registra overhead, o contraexemplo corrigido e os limites restantes.

[Otimização de caudas estruturadas](suffix-optimization-20261008.md) é o registro
histórico de `981e1c5`: overlap1800 de 42,09 para 4,37 s, com OR ainda quadrática.
A elegibilidade global e os caminhos alternativos dessa implementação foram
removidos pela unificação.

[Auditoria ampliada de explosão](explosion-review-20261008.md): matriz ampliada
30/30 PASS com heap de 4 GiB e orçamento de 100 milhões, incluindo 117 mil
atribuições e overlap1800 dinâmico. Falhas da rodada com heap/orçamento menores
continuam registradas; essa qualificação adicional usa recursos diferentes
dos benchmarks abaixo.

Artefato medido: commit `f70a94b`, SHA completo e hash do JAR em
[pins.json](pins.json). Os JSONs, comandos, logs, tempos e RSS por fonte
permanecem em `results/` (ignorado pelo Git); os resultados normalizados e
contadores estão em [report.json](report.json). Os relatórios versionados guardam
contagens por fonte, hashes e todas as diferenças; conjuntos completos ficam
preservados em `results/final-reports/`, com seus hashes registrados.

| Execução | Fontes completas | Tempo total | Maior RSS de um processo |
|---|---:|---:|---:|
| Pipeline: produtores congelados + consumidor de 8/10 | 73 | 482,2 s | 792,4 MiB |
| Monolito, execução final | 73 | 123,2 s | 464,4 MiB |
| Monolito, repetição | 73 | 121,2 s | 455,8 MiB |

A primeira execução final foi **3,91 vezes mais rápida**, com redução de
**41,40% no pico de RSS**. A referência integral congelada de 3/10 também foi
reexecutada: 532,7 s e 866,1 MiB. Não são estimativas de confiança estatística.

As 73 entradas incluem variantes de 45 nomes de programa. Em cada entrada,
comparamos a união deduplicada de dependências executáveis e source-qualified:
**815 relações iguais, 0 ausentes e 0 adicionais**. A união global contém
498 triplas distintas `(programa,tipo,destino)`; comparar por fonte evita que
uma variante esconda perdas em outra. Totais por fonte: COPY 507, programa 154,
FILE 138, SQL INCLUDE 11 e tabela DB2 5. DCLGEN e logical-file são exercitados
pelos fixtures independentes. Os 73 JSONs finais são byte a byte idênticos na
repetição, conforme [determinism.json](determinism.json). Houve 26 fontes com
código 0 e 47 com código 1 (PARTIAL): candidatos iguais aos oráculos, mas com
incertezas locais explicitadas nos logs. Igualdade não significa fechamento
de todos os valores desconhecidos.

Também houve igualdade integral com a referência congelada reexecutada,
os resultados publicados de 8/10 e sua variante priority2; os quatro arquivos
`*-parity.json` registram as comparações por fonte e os hashes das saídas.

| Fase interna do monolito, soma das 73 fontes | Tempo |
|---|---:|
| Preprocessing e parsing | 47,93 s |
| Binding | 6,47 s |
| Controle, declarações e índice de consultas | 4,79 s |
| Dataflow | 44,46 s |
| Resolução das dependências | 0,14 s |
| Serialização e escrita do JSON | 1,93 s |

Startup Java, preparação e publicação atômica estão incluídos no tempo externo;
os contadores internos não precisam somar esse total. A referência mede etapas
mais amplas: frontend 188,16 s, lowering 199,95 s e dependências 94,10 s.

Método: mesmos 73 fontes, 185 hashes de fontes/includes, ordem de COPY,
UTF-8/FIXED, Temurin 21.0.12+1, Ryzen 5 5600GT e heap `-Xms32m -Xmx768m`.
Cada fonte inicia JVMs novas, sequenciais. Tempo externo inclui a execução e
publicação; normalização comparativa fica fora dele. RSS vem de GNU time.
Não houve limpeza do cache de arquivos. O monolito usa parser direto com
fallback ANTLR; a referência usa ANTLR. A comparação mede o fluxo inteiro.
Frontend/lowering da referência são os produtores congelados de 3/10, com
consumidor `843c837` de 8/10 e `--conservative-control`; não representa todos
os HEADs atuais da pipeline. Os SHAs e 22 hashes do runtime estão em pins.json.

[tests.json](tests.json): 280 testes específicos aprovados; pacote completo
com 1.720 aprovados, nenhum erro/falha e um teste futuro herdado opcional
desabilitado. FAST: 856 testes. Estresse CLI: 20/20 resultados esperados em
17,17 s, heap 512 MiB, pico 258,2 MiB, sem crash/OOM. São 18 fontes válidas
e dois negativos de sintaxe que devem falhar sem publicar JSON. Incluem os
insumos originais das explosões de contexto e memória. O único oráculo antigo
divergente de NEXT SENTENCE foi investigado e corrigido no esperado local,
preservando o original e sua justificativa na
[procedência dos fixtures](../src/test/resources/dependency-regression/README.md).
Incerteza externa, representações complexas, contagem exata de PERFORM TIMES
acima de uma iteração e extensões de tabela desconhecidas
continuam com os limites descritos no README; não há afirmação de paridade
universal de COBOL.

Reproduzir a partir da raiz deste clone (referências externas somente leitura):

```sh
mvn package
python3 -B benchmark/smoke-standalone.py target/cobol-dependency-analyzer.jar
python3 -B benchmark/run-carddemo.py ../artefatos-e2e/carddemo-scanner-discovery-20261003/results.json benchmark/results/carddemo
python3 -B benchmark/run-reference.py ../artefatos-e2e/carddemo-scanner-discovery-20261003/results.json benchmark/results/reference /tmp/provisional-dependency-validation-20261008
python3 -B benchmark/compare-reference.py ../artefatos-e2e/carddemo-scanner-discovery-20261003/results.json benchmark/results/carddemo benchmark/results/reference benchmark/results/parity.json
python3 -B benchmark/run-resource.py benchmark/results/resource
python3 -B benchmark/summarize.py benchmark/results/carddemo benchmark/results/reference benchmark/results/resource benchmark/results/report.json
```

Omitir o terceiro argumento de run-reference.py usa o consumidor congelado.
Os testes Maven e o smoke do JAR não dependem de nenhum desses repositórios.
