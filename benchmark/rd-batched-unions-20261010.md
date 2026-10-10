# União em lote no reaching definitions

Baseline publicada: `7a0b02a386a7a98b81d90ca0acb0c3c8f21e7ca6`.
[Resultados e hashes](rd-batched-unions-20261010.json).

Um hub com N entradas recebia os candidatos um a um. Para construir o resultado
final, o solver congelava conjuntos com 1, 2, 3, …, N elementos. Os prefixos eram
temporários, mas sua construção copiava aproximadamente N²/2 elementos. O
discovery com JFR e instrumentação em cópias do código localizou esse custo na
união dos pais em `SparseDefinitions.propagate`, especialmente em `Set.copyOf`.
Em N=16.384, a cópia instrumentada congelou 268.632.000 elementos ao longo da
execução; o protótipo em lote congelou 196.614, preservando os resultados.

A implementação agora reutiliza um conjunto imutável que contenha os candidatos
recebidos. Quando nenhum deles contém a união, acumula as entradas em um único
`HashSet` local e congela apenas o resultado final. A informação desconhecida é
combinada separadamente, por OR, para sobreviver também à reutilização de um
conjunto maior. O caminho de propagação, as operações COBOL e o ponto fixo
continuam os mesmos. A mudança não introduz limites de profundidade ou descarte
de candidatos.

## Medição do JAR final

Fixture `dynamic-only`, dois hubs e caixinhas com GO TO de retorno e PERFORM
cruzado. JVMs sequenciais, OpenJDK 25.0.4, SerialGC, dois processadores ativos,
`--solver reaching-definitions`, `--max-work` padrão de 100 milhões.

| N | LOC | Heap | Total antes → depois | Dataflow antes → depois | Pico RSS antes → depois |
|---:|---:|---:|---:|---:|---:|
| 2.048 | 46.108 | 2 GiB | 7,12 → 6,76 s | 1,63 → 1,22 s | 762,9 → 742,5 MiB |
| 16.384 | 368.668 | 6 GiB | 392,98 → 79,94 s | 333,14 → 19,08 s | 6.040,2 → 6.079,5 MiB |

N=16.384 ficou **4,92× mais rápido no total e 17,46× no dataflow**. Não houve
redução material do pico de RSS. As duas saídas são byte a byte iguais às da
baseline e correspondem ao oráculo: 2.049 e 16.385 nomes, respectivamente. Os
contadores estruturais e de trabalho do reaching definitions também são iguais;
o ganho vem do custo de construir as uniões.

São execuções individuais, não medianas. O teste grande não teve corte de tempo;
teve proteção de RSS em 6.656 MiB e reserva de memória do host de 3.072 MiB.
Nenhuma proteção foi acionada. Código 1/PARTIAL mantém explícita a aproximação
de correlações deste solver. O resultado não demonstra complexidade linear de
toda a análise nem qualifica o fonte corporativo.

## Qualificação

- 64 testes focados; FAST com 1.100 testes, sem falhas ou skips.
- `qualification-local`: PASS; suíte completa com 1.807 testes, zero falhas e
  um skip preexistente, condicionado a `semantic.condition.required=true`.
- CardDemo: 73 fontes nos dois solvers, JSONs idênticos e 815 relações por modo.
- Standalone: 36/36 idênticos no reaching definitions. No preciso, 35/36
  idênticos; `return-fanout-128` reproduz o OOM da baseline com heap de 128 MiB.
- No total, 218 execuções do corpus: 217 saídas idênticas e um limite da baseline
  reproduzido. Nenhuma perda ou dependência adicional nova. As cinco diferenças
  de correlação entre os solvers, já conhecidas nas fixtures, permanecem.
- JAR standalone testado em diretório e ambiente isolados. Atomicidade em falha
  de recurso e seleção de solver continuam cobertas pelos testes de CLI.

A primeira tentativa do gate local completo revelou um comando de regressão
herdado que passava `--copybooks` ao CLI do analisador. O script agora seleciona
`ExplorerMain` explicitamente; a propriedade Maven mantém `DependencyMain` como
padrão. FAST e qualificação local foram repetidos depois da correção. Logs da
tentativa inicial, da repetição, corpora, GC, comandos e telemetria ficam em
`benchmark/results/rd-batched-union-20261010/`, ignorado pelo Git. O discovery
instrumentado permanece em `benchmark/results/candidate-cost-20261010/`.

## Reprodução da fixture

O gerador versionado produz o mesmo fonte congelado, com uma transformação única:

```python
import importlib.util, re
from pathlib import Path
spec = importlib.util.spec_from_file_location("hub", "benchmark/generate-hub-dispatch.py")
hub = importlib.util.module_from_spec(spec)
spec.loader.exec_module(hub)
n = 16384
text = re.sub(r"    CALL 'PGM(\d{5})'\.",
    lambda m: f"    CALL TARGET-PGM.\n           MOVE 'PGM{m[1]}' TO TARGET-PGM.\n           CONTINUE.",
    hub.source(n, 2, "return-fanout"))
Path("/tmp/dynamic-only-16384.cbl").write_text(text)
```

Execute o JAR com `-Xms16m -Xmx6144m -XX:MaxMetaspaceSize=128m
-XX:MaxDirectMemorySize=32m -XX:ActiveProcessorCount=2 -XX:+UseSerialGC
-XX:+ExitOnOutOfMemoryError`, solver `reaching-definitions`, fonte gerado e
`--metrics`. Os hashes no relatório permitem verificar fonte, JAR e saída.
