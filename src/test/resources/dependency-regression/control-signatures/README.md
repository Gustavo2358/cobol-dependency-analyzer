# Combinações de flags no mesmo corpo

`flags-10-predicates.cbl` provoca a especialização de um mesmo corpo por dez
flags binárias. Os campos INPUT não têm VALUE e são desconhecidos para o
analisador. Cada CHOOSE testa um INPUT, atribui ONE00000 ou ZERO0000 à sua FLAG
e invoca o próximo parágrafo em um dos dois branches. BODY testa as dez flags
antes dos CALLs. Todos os PERFORMs retornam, e todas as consultas são alcançáveis.

O fonte tem 174 linhas e permite 1.024 combinações na entrada de BODY. O fonte
não enumera essas combinações: cada execução concreta escolhe um branch por
nível e chega a BODY uma única vez. Não há ciclo, recursão, THRU ou handler.

O oráculo independente é pequeno: a união deduplicada das dependências contém
exatamente os programas ONE00000 e ZERO0000, sem diagnóstico de incompletude.
Ambos são possíveis em cada nível, e nenhum outro destino aparece no fonte.
Assim, um crescimento do motor não pode ser atribuído ao tamanho da saída.

`flags-10-data.cbl` conserva as declarações e os parágrafos CHOOSE. BODY usa
diretamente `CALL FLAG-01`, `CALL FLAG-02` etc., sem testar as flags em IFs.
Tem as mesmas duas dependências; as localizações e os sites são diferentes.
Esse controle mantém os valores relevantes como dados paramétricos, em vez
de entradas necessárias para determinar branches dentro de BODY.

Os dois fontes, seus hashes e nomes esperados estão em `expected.json`.
`DependencySummaryTest` verifica esse oráculo. O teste não fixa a quantidade
atual de contextos: uma melhoria futura deve poder reduzi-la preservando os
resultados. O [relatório da medição](../../../../../benchmark/control-signatures-20261009.md)
registra o crescimento observado nas versões com 4, 6, 8, 10 e 12 flags.

Gerar outra escala, a partir da raiz do produto:

```sh
python3 -B benchmark/generate-control-signatures.py \
  --flags 10 --mode predicates --output /tmp/flags-10-predicates.cbl
python3 -B benchmark/generate-control-signatures.py \
  --flags 10 --mode data --output /tmp/flags-10-data.cbl
```

Executar o fixture de dez flags:

```sh
java -Xmx512m -jar target/cobol-dependency-analyzer.jar \
  --source src/test/resources/dependency-regression/control-signatures/flags-10-predicates.cbl \
  --max-work 1000000 --metrics /tmp/flags-10-metrics.jsonl \
  --output /tmp/flags-10-dependencies.json
```

O gerador apenas escreve o fonte. A medição executou uma JVM por vez, mantendo
heap de 512 MiB e guardas externas de RSS 640 MiB e 30 segundos. Nenhuma escala
acima de 12 foi executada nesta campanha.
