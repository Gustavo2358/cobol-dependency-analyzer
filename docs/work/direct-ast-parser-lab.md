# Laboratório de parser DATA direto para AST

- id: DIRECT-AST-LAB-001
- title: Parser próprio de declarações DATA com AST direta
- status: IN_PROGRESS
- scope: laboratório opt-in no frontend; branch `lab/direct-ast-parser`; sem merge ou mudança de contratos downstream.

## Implementação

**Estado atual: [73/73 programas CardDemo sem fallback em DATA](direct-ast-complete.md).**
Os resultados de três programas e o inventário de 70 fallbacks abaixo são históricos, anteriores à ampliação.

`--parser direct-data-lab` ativa um parser descendente recursivo próprio para
um subconjunto de DATA DIVISION. O padrão continua `--parser antlr`.
O reconhecimento produz declarações tipadas e materializa a AST imutável,
sem construir contexts ANTLR para o corpo DATA admitido nem executar o visitante
DATA original. Preprocessing, lexer e as demais divisions continuam usando ANTLR.
O modo cobre DATA; lexer e PROCEDURE ainda não foram substituídos.

A implementação deriva das regras atuais de `Cobol.g4`. A regra PIC original
permanece intacta para o controle ANTLR. A leitura direta de PIC é linear.
O reconhecimento próprio cobre as formas DATA presentes nos 73 programas do
CardDemo fixado: WORKING-STORAGE, LINKAGE, FILE SECTION com FD/SD, RECORD e
RECORDING MODE; níveis 1–49, 77 e 88; nomes da produção `cobolWord` e nomes
omitidos; PIC, USAGE, VALUE explícito/implícito, intervalos e figurative ALL;
REDEFINES; OCCURS com limites, DEPENDING qualificado, chaves e índices;
declarações EXEC SQL preservadas opacas. LOCAL-STORAGE sem LD e cláusulas simples
de visibilidade/alinhamento de apresentação continuam admitidas.

A cobertura não usa nomes de programas ou listas de arquivos em produção.
O teste de paridade verifica o conjunto de tokens de `cobolWord` contra a gramática.
FILE e OCCURS produzem fatos tipados; o registro de origens não substitui a
construção semântica. O gate `DirectDataCorpusCheck` exige simultaneamente
admissão própria sem fallback e equivalência integral da AST/origens/coverage.

Construções fora do recorte, como RENAMES, referências subscriptadas em OCCURS,
LOCAL-STORAGE com LD e cláusulas FILE não implementadas, ainda devolvem **toda a
DATA DIVISION** ao parser ANTLR. A posição original dos tokens é restaurada e
nenhuma declaração parcial é publicada. Erros internos não são capturados como
fallback. Cada execução possui uma sessão independente, sem cache global.
Logs registram admissão e motivo de fallback. A aprovação do corpus exige zero
fallback em todos os 73 programas; ter um exit code zero não basta.

Um registro plano de origens preserva IDs, regras, spans e linhas da apresentação
sintática. Ele não reconstrói semântica; a AST consome os drafts tipados. IDs de AST,
provenance de COPY/modelos, coverage, diagnósticos e contratos do Semantic Product
são preservados. A equivalência é uma propriedade testada no corpus descrito abaixo,
não uma garantia matemática para qualquer entrada.

## Autoridade e invariantes

A autoridade de compatibilidade deste laboratório é a gramática e a AST vigentes;
não se introduz nova interpretação do dialeto. Referências oficiais das construções:
[IBM, data description format 1](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=entry-format-1), [IBM, OCCURS](https://www.ibm.com/docs/en/cobol-zos/6.3.0?topic=entry-occurs-clause),
[IBM, RECORD](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=entries-record-clause)
e [IBM, regras de PICTURE](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=clause-data-categories-picture-rules).
Entradas permissivas da gramática atual não são silenciosamente corrigidas.
Aplicam-se INV-AST-001/002/003 e os contratos de provenance e coverage existentes.
`DirectDataParserTest` compara a estrutura completa, metadados e erros com o parser
original e exige que casos positivos realmente usem o caminho direto.

## Medição reproduzível

Compilar antes da medição. Gerar o classpath da aplicação com Maven:

```bash
mvn -q -DskipTests compile
mvn -q exec:exec -Dexec.executable=echo '-Dexec.args=%classpath'
python3 scripts/direct-data-lab.py --classpath '<classpath obtido acima>' \
  --source corpus/carddemo/cbl/COACCT01.cbl \
  --source corpus/carddemo/cbl/COACTUPC.cbl \
  --source corpus/carddemo/cbl/COTRTUPC.cbl \
  --copybooks corpus/carddemo/cpy,corpus/carddemo/cpy-bms,corpus/cpy,corpus/cpy-bms \
  --repetitions 5 --output /tmp/direct-data-lab-new
```

O diretório de saída deve ser novo. São JVMs frias independentes, ordem AB/BA
alternada, G1, heap 256 MiB–2 GiB, JSON sem compressão nos dois modos.
A métrica principal soma reconhecimento, indexação de origens sintáticas e
construção da AST, incluindo o trabalho do registro de origens. Exclui preprocessing,
lexer e exportação nos dois modos. Tempo total da CLI, CPU e RSS são separados.
O executor preserva comandos, logs, hashes de fontes/COPY/build/dependências e
compara cada artefato publicado byte a byte; diferenças não são normalizadas.

## Validação inicial e limites históricos

- Nove testes diferenciais, incluindo PIC, VALUE, hierarquia, Unicode, CRLF,
  erros, múltiplos programas, isolamento e fallback.
- Varredura de 332 fixtures: 308 comparadas, 223 exercitando o caminho direto;
  23 rejeitadas pela normalização FIXED antes do parser; uma falha preexistente
  reproduzida nos dois modos (`source-dependencies-w3-db2/db2-delete/program.cbl`,
  NPE por `programId` ausente). Essas 24 entradas não contam como passes do parser.
- COACCT01 com preprocessing/COPY real: 2.582 declarações diretas e equivalência
  estrutural integral, incluindo provenance e modelos sintéticos.
- Suíte Maven completa: 1.312 testes, zero falhas/erros, um skip histórico.
- `qualification-local`: regressão de normalização executada; gate final de naming
  falha nos arquivos preexistentes `docs/work/index.md`, `json-zstd.yaml` e
  `post-antlr-performance.md`, idênticos à base `3646754b14519b0fa95f2a7233bdfce4527ebdba`.
  O gate não foi enfraquecido e esses documentos não foram alterados.

- FAST final: PASS, 37,2 s; inclui `DirectDataParserTest` no conjunto obrigatório.
- `lean.py docs`: PASS. Naming reproduzido separadamente na base com os mesmos três arquivos.

## Medição inicial — 2 de outubro de 2026

Temurin 25.0.4, cinco repetições por modo/programa (30 JVMs). Medianas:

| Programa | ANTLR → AST | Direto → AST | Redução | CLI ANTLR → direto | Admissão |
| --- | ---: | ---: | ---: | ---: | --- |
| COACCT01 | 1.433,1 ms | 616,4 ms | **57,0%** | 3,55 → 2,70 s | 2.582 declarações |
| COACTUPC | 2.721,9 ms | 2.696,4 ms | 0,9% | 5,47 → 5,48 s | fallback OCCURS |
| COTRTUPC | 947,7 ms | 978,8 ms | −3,3% | 2,72 → 2,75 s | fallback OCCURS |

No COACCT01, reconhecimento isolado caiu de 1.203,0 para 415,3 ms (**65,5%**).
A redução da CLI foi **23,9%**. A faixa da métrica combinada foi 1.412,7–1.457,6 ms
no ANTLR e 604,6–624,4 ms no modo direto. CPU mediana caiu de 9,60 para 8,48 s;
RSS mediano de 468.648 para 377.508 KiB. São observações deste ambiente e perfil frio,
sem extrapolação para serviço persistente/JVM aquecida ou outro hardware.

**285 pares de artefatos idênticos byte a byte**: 19 produtos × 5 pares × 3 programas,
incluindo AST, apresentação e Semantic Product. Os dois programas com OCCURS
não demonstram ganho consistente; COTRTUPC apresentou regressão de tempo na amostra.
A meta de 50% foi alcançada somente no programa admitido pelo parser próprio.

[Amostras, dispersão e hashes da implementação medida](direct-ast-parser-lab-results.json).
Evidência bruta preservada na raiz agregadora em `.direct-ast-lab/final-evidence/`;
logs de FAST e qualificação ficam em `.direct-ast-lab/fast-final.log` e
`.direct-ast-lab/qualification-local.log`. O manifesto registra o SHA base e os
hashes do código/build medido antes do commit desta branch. Nenhum baseline foi regravado.

## Próxima decisão

Promover para produção exige revisão humana e ampliação da admissão com testes
diferenciais. A ampliação de DATA é autorizada nesta sessão até eliminar fallback nos 73
programas. Ganho uniforme também exige medir os gargalos restantes de PROCEDURE. Os resultados
positivos de DATA não justificam prometer 50% na CLI ou em todo o corpus.

## Ampliação da medição para o corpus completo

A [rodada integral posterior](direct-ast-corpus.md) cobre 73 variantes CardDemo
e 13 fontes complementares do checkout, com uma rodada por modo a pedido do usuário.
No conjunto principal, três programas usam o parser próprio e 70 caem em fallback.
COACCT01 e CODATE01 superam 50% de redução na etapa sintática; COBSWAIT não.
As divergências brutas de serialização e os limites da amostra estão discriminados.

## Ampliação da implementação para 73 programas

A implementação foi ampliada após o levantamento dos fallbacks. `DirectDataCorpusCheck`
passou nos 73 fontes preprocessados: 73 admissões próprias, zero fallback e zero
diferenças estruturais, incluindo origens, AST, coverage e diagnósticos. A regressão
de fixtures comparou 308 entradas, com 240 usando o caminho direto; as 23 rejeições
de normalização e a falha preexistente continuam explicitadas. FAST local passou.
A [confirmação pela CLI completa](direct-ast-complete.md) também passou: 73/73
programas, zero fallback, 35.590 declarações, 146 execuções e 1.387 pares de produtos
com conteúdo equivalente. Uma divergência bruta de ordem de chaves JSON permanece
discriminada. FAST local/remoto PASS; 1.317 testes completos, zero falhas/erros e um
skip histórico. O naming preexistente continua bloqueando o gate final da qualificação.
No total dos 73 programas, parsing combinado caiu 11,9% e a CLI caiu 4,1%; a meta
universal de 50% não foi atingida. Lexer e PROCEDURE continuam no ANTLR.
