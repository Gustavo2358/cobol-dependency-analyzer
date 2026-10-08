# Oráculos de dependências

Os fontes COBOL foram copiados sem alteração do checkout `analysis-cfg` no
commit `c09c6712625f21d3155214406549e4a13de92ff4`:

- `analysis-adapters/src/test/resources/cp6/perform-completion`: 25 casos.
- `cp6/logical-alias-move`: 14 casos.
- `cp6/cics-control-completion`: 30 casos.
- `logical-text-w2`: 24 casos, incluindo correlação de grupos e negativos.
- `source-possibility`: 14 casos; os destinos são a união deduplicada de
  `dependencies.programs` e `sourceQualifiedDependencies.occurrences` dos JSONs
  de referência, reduzida a `expected-programs.json`.
- `file-dependencies/w8`: 41 casos CICS FILE; `expected-files.json` contém
  somente destinos de arquivos, derivados dos comandos/assignments e dos
  oráculos manuais da família FILE. SYSID não faz parte do novo schema.

A correção validada de NEXT SENTENCE está em
`source-possibility/validated-divergences.json`; o oráculo original não foi
alterado para simular equivalência. Os JSONs brutos extraídos e seus hashes
ficaram em `.tmp/reference-oracles/` durante esta validação local.

Os três primeiros oráculos especificam candidatos por CALL. O teste limita essa
comparação às linhas dos CALLs, pois esses mesmos fontes também têm operandos
CICS PROGRAM. A saída pública deduplica destinos por programa; testes unitários
separados verificam BEFORE, sobrescrita, contextos, provenance e deduplicação.

Os 55 fixtures COPY/DCLGEN/INCLUDE/DB2 já presentes em
`src/test/resources/cobol/source-dependencies-w3*` também são consumidos pelo novo
analisador. Os oráculos SQL dessa família especificam tabelas; os de artefatos
especificam COPY/DCLGEN/SQL INCLUDE. A comparação CardDemo cobre as categorias
juntas, sem filtrar destinos adicionais.

Não há dependência dos repositórios da pipeline para executar esses testes.

A família `resource-stress` inclui 20 fontes reais: CTXBOOM e seus controles
(2026-10-02), ativação AND/OR com 76 faixas, sobreposição com 900 faixas
(2026-10-05) e 12 casos plain/escape/CICS da campanha contextual (2026-10-03),
comparados com os destinos republicados em 2026-10-08. Há 18 fontes válidos e
2 negativos de parsing: os casos fixed-pasted/fixed-star têm tokens numéricos
após COPY na área B; o parser de referência recuperava esses erros. O novo CLI
rejeita o fonte e preserva a saída anterior. Isso é uma divergência explícita
na política de falha, não uma comparação aprovada de um programa válido.
Fontes, hashes, procedência e destinos estão em `resource-stress/expected.json`.
Sem consultas operacionais, a análise publica os fatos de fonte sem explorar
controle/dataflow; isso elimina o custo da sobreposição irrelevante de faixas.
