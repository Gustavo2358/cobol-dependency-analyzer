# Laboratório de parser DATA direto para AST

- id: DIRECT-AST-LAB-001
- title: Parser próprio de declarações DATA com AST direta
- status: IN_PROGRESS
- scope: laboratório opt-in no frontend; branch `lab/direct-ast-parser`; sem merge ou mudança de contratos downstream.

## Implementação

`--parser direct-data-lab` ativa um parser descendente recursivo próprio para
um subconjunto de DATA DIVISION. O padrão continua `--parser antlr`.
O reconhecimento produz declarações tipadas e materializa a AST imutável,
sem construir contexts ANTLR para o corpo DATA admitido nem executar o visitante
DATA original. Preprocessing, lexer e as demais divisions continuam usando ANTLR.
Este é o primeiro experimento de substituição; não é um parser COBOL completo.

A implementação deriva das regras atuais de `Cobol.g4`. A regra PIC original
permanece intacta para o controle ANTLR. A leitura direta de PIC é linear.
WORKING-STORAGE, LINKAGE e LOCAL-STORAGE admitem níveis 1–49, 77 e 88,
nomes IDENTIFIER/FILLER, PIC, USAGE, VALUE explícito ao final da declaração,
REDEFINES simples, GLOBAL, EXTERNAL sem BY, JUSTIFIED, SYNCHRONIZED e BLANK.
O código e seus testes definem a admissão exata; essa lista não declara suporte
completo às combinações permitidas pelo dialeto.

OCCURS, RENAMES, FILE SECTION, nomes reservados ambíguos, VALUE implícito e
outras construções fora desse recorte devolvem **toda a DATA DIVISION** ao parser
ANTLR, restaurando a posição original dos tokens. Nenhuma declaração parcial é
publicada. Erros internos não são capturados como fallback. Cada execução possui
uma sessão independente, sem cache global. Logs registram admissão e motivo de fallback.

Um registro plano de origens preserva IDs, regras, spans e linhas da apresentação
sintática. Ele não reconstrói semântica; a AST consome os drafts tipados. IDs de AST,
provenance de COPY/modelos, coverage, diagnósticos e contratos do Semantic Product
são preservados. A equivalência é uma propriedade testada no corpus descrito abaixo,
não uma garantia matemática para qualquer entrada.

## Autoridade e invariantes

A autoridade de compatibilidade deste laboratório é a gramática e a AST vigentes;
não se introduz nova interpretação do dialeto. Referências oficiais das construções:
[IBM, data description format 1](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=entry-format-1)
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

## Validação e limites

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

## Resultado final — 2 de outubro de 2026

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
diferenciais. Para alcançar outros programas, o próximo recorte é OCCURS; para
ganho uniforme, medir e substituir também os gargalos de PROCEDURE. Os resultados
positivos de DATA não justificam prometer 50% na CLI ou em todo o corpus.

## Ampliação da medição para o corpus completo

A [rodada integral posterior](direct-ast-corpus.md) cobre 73 variantes CardDemo
e 13 fontes complementares do checkout, com uma rodada por modo a pedido do usuário.
No conjunto principal, três programas usam o parser próprio e 70 caem em fallback.
COACCT01 e CODATE01 superam 50% de redução na etapa sintática; COBSWAIT não.
As divergências brutas de serialização e os limites da amostra estão discriminados.
