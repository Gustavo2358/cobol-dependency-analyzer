# FIXTURE02 reconstruída

Fonte fornecida em 8/10/2026: 2.000 campos, 150 parágrafos com MOVE, três IF,
PERFORM, CALL e GO TO cruzados, mais FINAL-PARA. São 4.114 linhas.
`fixture02.free.cbl` conserva o formato livre e a ressalva fornecida: somente
seis parágrafos foram conferidos com o vídeo; a igualdade dos demais com o
vídeo não foi verificada. A sequência usada é Python `random.Random(1)`,
com sorteios na ordem dos operandos escritos no fonte.

O CLI aceita FIXED. `fixture02.fixed.cbl` adiciona sete espaços às linhas de
código e converte os comentários iniciais para a coluna 7. Não altera comandos,
ordem, operandos ou destinos. Nenhuma linha de código excede a coluna 72.
Hashes e oráculo estão em `expected.json`.

O esperado operacional é vazio. Todo parágrafo P00000–P00149 tem um PERFORM
incondicional antes do seu CALL. Não há base de retorno: a cadeia que parte de
P00000 entra num ciclo em P00063. Os IFs anteriores não pulam o PERFORM. Nenhum
CALL é alcançado, e os GO TO incondicionais impedem queda até FINAL-PARA.
Esse oráculo vem do fluxo do fonte, independentemente da saída do analisador.
O fixture exercita preparação de relevância e recursão; não demonstra custo de
150 consultas alcançadas nem combinações independentes de 450 predicados.

Reproduzir a partir da raiz do projeto, com o JAR empacotado:

```sh
java -Xms32m -Xmx4096m -jar target/cobol-dependency-analyzer.jar \
  --source src/test/resources/dependency-regression/video-reconstructed/fixture02.fixed.cbl \
  --output /tmp/fixture02-dependencies.json \
  --metrics /tmp/fixture02-metrics.jsonl --max-work 100000000
```

Resultado esperado: código 0, sem diagnóstico de produto,
`{"program":"FIXTURE02","dependencies":[]}`.
A medição e comparação estão no relatório de relevância, referenciado no
[README da aplicação](../../../../../README.md).
