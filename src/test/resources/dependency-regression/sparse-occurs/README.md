# Demanda de posições de OCCURS

O script `generate-sparse-occurs.py` gera as 23 fontes e os hashes/oráculos de
`expected.json`. Cada caso verifica candidatos exatos e a presença ou ausência
de `DYNAMIC_REMAINDER`; valores de outras posições e valores sobrescritos não
podem aparecer no resultado.

A matriz cobre extensão crescente com dois acessos, duas dimensões, CALL literal
em loop, índice resolvido por MOVE/aritmética/PERFORM, conteúdo desconhecido,
leitura e escrita com índice desconhecido, cobertura completa, VALUE persistente,
INITIALIZE, REDEFINES, texto de grupo, escrita parcial e chamadores distintos.
`computed-perform` conserva a limitação existente: COMPUTE abre IDX e a consulta
publica GOOD/OTHER com remainder. Não é o oráculo de índice aritmético fechado;
este está em `arithmetic-index`.

A baseline foi executada antes da alteração de produção, em JVMs sequenciais,
heap 512 MiB, RSS supervisionado em 640 MiB, timeout 45 s e orçamento 10 mil.
Três casos de escala atingem limites artificiais na baseline; o candidato deve
concluir todos com o mesmo orçamento. A rodada adicional com orçamento padrão
um milhão distingue o limite artificial do custo real da expansão.

`DependencyAnalyzerTest` executa a matriz no FAST e exige no máximo quatro
posições materializadas e menos de doze valores rastreados por caso. Testes
adicionais verificam correlação de índices, descoberta encadeada de posições,
default de posições não escritas, reconstrução de grupos, índice inválido e
preservação do output anterior após falha de recursos.

Comandos de medição, comparação, hashes e limites estão no
[README da aplicação](../../../../../README.md).
