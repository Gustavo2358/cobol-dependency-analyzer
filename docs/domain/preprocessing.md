# Preprocessing

## Propósito e escopo

`PreprocessorEngine` aplica a política fechada do frontend preprocessador, expande COPYs e transporta o `SourceMap` até o lexer COBOL.

## Entradas e saídas

- **Entrada:** fonte normalizada e mapeada, binding das gramáticas e `CopybookLibrary`.
- **Saída:** `Outcome` imutável com texto, mapa composto, diagnostics tipados, contagens derivadas, compiler options e modos `PGMNAME`, `DYNAM` e `DLL`.

## Políticas atuais

Cada alternativa top-level de `CobolPreprocessor.startRule` possui classificação explícita:

- `COPY` é expandido, incluindo nesting e `REPLACING` suportado;
- `EJECT`, `SKIP1`, `SKIP2`, `SKIP3` e `TITLE` são branqueados preservando quebras de linha;
- compiler options são extraídas e transportadas para a policy de resolução;
- EXECs são preservados por fronteira opaca para o parser COBOL;
- texto COBOL comum é mantido;
- `REPLACE` top-level e `REPLACE OFF` permanecem `UNSUPPORTED` e falham antes do parser COBOL.

COPY ausente, cíclico ou com erro de I/O produz placeholder mapeado e diagnostic. Para membro não encontrado, `Diagnostic.Code.UNRESOLVED_COPY` é a identidade semântica estruturada; `Outcome.unresolved()` é derivado desses fatos, que preservam nome solicitado e localização em ordem determinística. A mensagem humana continua útil, mas seu wording não participa de contagem, composição ou geração de gaps. Ausência de copybook mantém a execução observável como incompleta; não equivale a COPY vazio nem exige interromper fases posteriores quando o placeholder ainda permite construir seus produtos coerentemente. COPY cíclico e falha de I/O conservam a política anterior e não pertencem a esse fallback.

## Modelos estruturais DFH

Na ausência de `DFHAID` ou `DFHBMSCA` nas bibliotecas configuradas, um COPY sem
qualificação nem REPLACING usa o perfil `ibm-cics/structural-v2`. O modelo fornece
os grupos 01, campos 02 `PIC X` e condições 88 documentados no IBM CICS Primer,
na lista AID e nas constantes BMS. São 36 campos AID (incluindo `DFHNULL`),
69 campos BMS e `DFHERASE`/`DFHCURSR` subordinados a `DFHBMFLG`.
`DFHENTER OF DFHAID` e as condições qualificadas usam o binding canônico.
A [definição e as fontes IBM](../work/synthetic-dfh-structure.md) delimitam a
completude: união das declarações documentadas, sem alegar reprodução binária
de cada versão instalada do CICS.

Os campos têm tipo e tamanho declarados. Seus valores iniciais ficam ausentes;
os dois níveis 88 preservam os conjuntos hexadecimais documentados. Valores de
modelo não são fatos de runtime. Um membro real sempre tem prioridade. Não há
regra por prefixo DFH, consulta de rede ou fallback de SQL INCLUDE. REPLACING,
inclusive em COPY ancestral, desabilita o modelo; os demais membros, ciclos,
qualificações e erros de I/O seguem a política existente.

`NOMINAL_COPYBOOK` permanece como diagnóstico tipado de input parcial. SourceMap
marca a região modelada; `Ast.Meta.syntheticModel` conserva essa autoridade.
Provenance usa `model:ibm-cics/structural-v2/<membro>` e a cadeia real de inclusão.
A dependência COPYBOOK conserva autoridade COPY_SYNTAX e resolução RESOLVED.
O conteúdo físico ausente não se torna conhecido.

PIC e hierarquia não concedem prova de célula exata, bytes iniciais, disjointness
ou kill. SP 2.49 publica a confiança explícita dos símbolos em `nominalValues`.
O consumidor preserva candidatos anteriores e ambos os braços de condições
influenciadas por modelos. Fatos independentes e kills comprovados continuam
válidos. O [piloto anterior](../work/nominal-dfh-copybooks.md) registra as evidências
históricas; seus limites de nomes achatados foram substituídos por este perfil.

## EXEC DLI opaco

`execDliStatement` possui policy `PRESERVE_EMBEDDED_LANGUAGE` e um token
`EXECDLIBLOCK` próprio. A abertura é `EXEC`, separadores físicos e `DLI` com
fronteira de palavra. `DliRegion` percorre o slice normalizado em tempo linear,
com estados para literal (inclusive aspas duplicadas), comentário inline e
palavra. Apenas `END-EXEC` real fora desses estados fecha a região; EOF, literal
aberto, outro `EXEC` real e payload vazio/comentário-only falham explicitamente.
Não há fallback para `EXEC` desconhecido nem fechamento sintetizado pelo parser.

O transporte é `*>EXECDLI{` + slice normalizado original + `}*>ENDDLI`.
O segundo lexer valida novamente a fronteira lexical e exige o sufixo imediatamente
após o delimitador real. Cada região é um único token multiline; nenhum `+`
agrega blocos adjacentes. Chaves e tags dentro do payload não são delimitadores.
Espaços, comentários normalizados, Unicode e LF/CRLF/CR permanecem verbatim.
O período COBOL permanece fora do token. As regras `NEWLINE` das duas gramáticas
aceitam os três terminadores já preservados pelo normalizador.

A fronteira valida o enquadramento lexical, não comandos/opções IMS. Texto COBOL
acidental antes de um `END-EXEC` posterior, sem violação lexical ou novo `EXEC`,
não é distinguível de payload opaco. COPY REPLACING mantém o mecanismo existente;
se corromper o framing, o lexer COBOL falha em vez de publicar DLI recuperado.
As transformações usam `transformedSlice`/`replaceAll`, com origem aproximada,
sem alteração de SourceMap ou normalização. Esta capability não determina
referências, efeitos nem controle IMS.

## Provenance e determinismo

Expansões compõem os segmentos existentes e acrescentam `CopyFrame`; `REPLACING`
preserva origem aproximada. O resultado não recria identity map. Edições
top-level não sobrepostas são aplicadas em lote, mantendo a ordem estrutural do
fonte e dos diagnostics sem reconstruir o mapa a cada edição.

## Complexidade

O processamento percorre a parse tree e resolve COPYs pelo repositório configurado. Ciclos são detectados pela cadeia ativa; expansão não deve revarrer a codebase inteira.

## Fronteiras explícitas

O preprocessor não resolve símbolos COBOL, não interpreta payload de SQL/CICS/SQLIMS/DLI, usa os diretórios configurados e o catálogo estrutural explícito, sem inventar configuração ausente. Modos não especificados permanecem valores explícitos na policy posterior. A composição posterior, não o preprocessor, decide quais fases possuem pré-requisitos estruturais sob input incompleto.

## Evidência executável

`ExecDliOpaqueTest`, `ExecDliProvenanceTest`, `PreprocessorEnginePolicyTest`, `SourceNormalizationPreprocessingIntegrationTest`, `SourceProvenanceTest` e regressão do normalizador.

## Relações

Evals: EVAL-PRE-001, EVAL-PRE-002, EVAL-PROV-001 e EVAL-COV-003. Invariantes: INV-PROV-001, INV-PROV-002, INV-COV-001, INV-COV-002 e INV-COV-003. ADRs: ADR-0002, ADR-0007, ADR-0008 e ADR-0009.

## SQL INCLUDE with available source

Recall discovery found a PROCEDURE DIVISION member present in the configured
libraries whose COBOL CALL never reached the parser. The directive was inventoried
but its body was always left opaque. Under the [Db2 13 INCLUDE rule](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=statements-include),
INCLUDE replaces the directive with host-language/SQL source. It is legal in COBOL
Data/Procedure divisions; included members cannot contain another SQL INCLUDE.

The preprocessor now expands a grammar-proved SQL INCLUDE when its member is
available through explicit artifact configuration or configured source libraries.
It reuses normalization, source maps, include frames, active expansion protection
and recursive preprocessing. An explicit artifact mapping takes precedence over
library lookup. This does not classify a file as DCLGEN by name or extension.
Missing members retain the opaque input boundary; I/O failure remains explicit.
Nested SQL INCLUDE fails rather than publishing a fabricated expansion. Parsing,
nominal resolution and control qualification still run on the expanded program.
