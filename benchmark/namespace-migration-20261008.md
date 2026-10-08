# Migração para `com.imd.cobolexplorer` — 8/10/2026

Os pacotes Java, as coordenadas Maven e o ponto de entrada do produto usam
`com.imd.cobolexplorer`. Produção e testes foram movidos para essa árvore;
imports, referências por reflexão, logging, gramáticas, geradores, manifest,
launchers e seleção de classes dos testes foram atualizados. Não existem
wrappers, aliases ou classes mantidas no namespace anterior.

O `groupId` é `com.imd.cobolexplorer`, e o Main-Class é
`com.imd.cobolexplorer.DependencyMain`. Consumidores Java/Maven e launchers que
usam nomes de classes devem migrar. O nome do JAR, os argumentos do CLI e o
contrato JSON permanecem iguais. A URL pessoal foi retirada das instruções
atuais de compilação.

Base: `9088802421cfaae55a7d7e292d79b180c90ffeb7`.
Produção: `8fa1effd7ebf9ab2e80c0b2c3888c5f03306d21f`.
JAR validado: `872dc469296aed52a757371f2eefefddea2575be61b127ba765fc5b66daae428`.
JAR de referência: `98e1a30464b3c18349f6311c275f760fc3a032d9b5498cc5b741f4c7502782d4`.

## Escopo e identidade

Os 157 arquivos Java de produção são equivalentes aos da base após a
substituição dos pacotes. A única diferença adicional é
`DirectGrammar.GRAMMAR_SHA256`, regenerado porque o texto da gramática mudou;
as tabelas de reconhecimento são idênticas. O solver canônico não mudou.
Também foi removido um helper de pins sem chamadas; a validação vigente dos
pins Maven permanece e passou nos testes de política.

A auditoria do JAR confirmou o Main-Class e a ausência do identificador
pessoal anterior nos nomes das entradas e em seus conteúdos descompactados.
Nenhum produto original da pipeline foi alterado. O launcher usado para medir
a referência externa é lido do comando congelado, sem nome de pacote pessoal
hardcoded no script deste produto.

As coordenadas e imports externos `com.github.luben` pertencem à biblioteca
Zstandard e precisam corresponder ao artefato publicado. `.github/workflows`,
`GITHUB_*` e o contexto `github` pertencem à integração de CI. Esses nomes
funcionais foram preservados, assim como copyrights, licenças e evidências
históricas. O remote permanece necessário para a publicação do PR.

## Validação nova

| Verificação | Resultado |
|---|---|
| FAST obrigatório | 856 aprovados; zero falhas, erros ou skips |
| Suíte Java completa e `mvn -o -q package` | 1.734 aprovados, zero falhas/erros, um skip opcional |
| Política e naming | 13 e 19 testes aprovados |
| Gerador direto | `--check` aprovado; 616 produções, 2.421 expressões |
| JAR isolado | CWD/ambiente isolados e resultado dinâmico mínimo aprovados |
| Casos originais | 20/20; 18 fontes válidas e dois negativos de sintaxe |
| Matriz ampliada de estresse | 30/30; fontes, JSONs, códigos e diagnósticos preservados |
| CardDemo | 73/73; 815 relações iguais, zero ausentes/adicionais |

O skip é o teste herdado opt-in
`SemanticConditionContextDiscoveryTest.requiredSemanticOraclesForFutureImplementation`;
a propriedade `semantic.condition.required` não foi habilitada. Foi executada
a suíte Java completa, sem alegar execução integral do harness externo herdado.

As rodadas CLI usam JVMs novas e sequenciais, Temurin 21, heap de 4 GiB,
orçamento de 100 milhões e timeout de 300 s por entrada. CardDemo levou
150,54 s agregados, com pico de 702,0 MiB. Antes da execução, foram verificados
os 185 hashes de fontes/includes da referência. Seus 73 JSONs são byte a byte
iguais aos da versão anterior, com os mesmos códigos e diagnósticos de produto:
26 entradas com código 0 e 47 com código 1 (PARTIAL). Igualdade com o oráculo
não fecha essas incertezas.

A primeira tentativa FAST encontrou um hash gerado desatualizado e um prefixo
antigo no teste por reflexão; ambos foram corrigidos antes da rodada final.
Uma invocação CardDemo com argumentos mal separados foi rejeitada pelo CLI,
antes de executar fontes. Esses logs permanecem preservados e não contam como
rodadas aprovadas.

Os hashes dos logs, dados brutos e resultados por entrada estão em
[namespace-migration-20261008.json](namespace-migration-20261008.json).
Os artefatos completos permanecem localmente em `results/namespace-*-20261008/`
e `.tmp/`, sem versionar builds ou caches. As saídas anteriores do solver
unificado foram reutilizadas apenas como referências diferenciais; todas as
contagens de validação acima vêm de execuções novas. Esta migração não afirma
ganho de desempenho ou fechamento semântico adicional.
