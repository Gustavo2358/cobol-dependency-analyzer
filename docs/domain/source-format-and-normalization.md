# Source format e normalização

## Propósito e escopo

`SourceNormalizer` converte registros COBOL físicos para a entrada do preprocessor sem perder provenance. O domínio suportado atual é fixed-format com policy explícita para debug lines; outros formatos não são aceitos implicitamente.

## Entradas e saídas

- **Entrada:** texto bruto, nome do arquivo, `SourceFormat.FIXED` e `DebugLinePolicy`.
- **Saída:** `SourceNormalizer.Result` com texto normalizado, `SourceMap`, diagnostics e formato efetivo.

O mapa é originado no texto bruto. A normalização preserva terminadores LF,
CRLF e CR por registro e preserva conteúdo Unicode verbatim. Offsets e colunas
são medidos em code points; separadores Unicode de linha falham localmente. TAB separador segue a política
interna de importação abaixo.

## Contrato fixed-format atual

- colunas 1–6: sequence area;
- coluna 7: indicator area;
- colunas 8–72: program text;
- conteúdo posterior: identification area, fora do texto compilável.

A indicator area possui catálogo fechado: blank, comentário `*`, page-eject `/`, continuação `-` e debug `D`/`d`. Indicador desconhecido não recebe fallback.

Continuação é resolvida por estado lexical para literal com aspas simples, literal com aspas duplas ou palavra. Continuação órfã, após registro incompatível ou fora das categorias suportadas produz diagnóstico/erro localizado; paridade simples de aspas não é o algoritmo.

Comment entries são derivadas dos owners reconhecidos pela gramática e pelas fronteiras de Area A. Pontos dentro do conteúdo não encerram a entrada; `END-REMARKS` é fronteira explícita quando aplicável. Consulte [ADR-0001](../architecture/decisions/0001-comment-entry-normalization.md).

## Incerteza e diagnostics

Formato, separador de linha, indicador ou continuação não suportados falham na
fronteira correspondente. A normalização não tenta reparar entrada de modo
silencioso. Conteúdo transformado conserva origem com `exact=false`.

## Complexidade e provenance

O scanner percorre os registros e mantém estado limitado, com custo linear no tamanho da entrada. Nenhuma linha física é inserida para facilitar o parser. O contrato completo de origem está em [provenance](provenance.md).

## Evidência executável

`SourceNormalizerTest`, `SourceProvenanceTest`, fixtures em `src/test/resources/cobol/source-format/` e `scripts/source-normalizer-regression.sh`.

## Relações

Evals: EVAL-SRC-001, EVAL-SRC-002, EVAL-PRE-002 e EVAL-PROV-002. Invariantes: INV-AST-002, INV-PROV-001, INV-PROV-002 e INV-COV-002. ADRs: ADR-0001, ADR-0002 e ADR-0009.

## Importação de TAB (R3-A)

Antes de interpretar as áreas fixas, HT separador avança até a próxima parada
de quatro colunas (5, 9, 13, … em coordenadas de coluna 1-based), e não adiciona
sempre quatro espaços. A mesma regra é aplicada a cada fonte e COPY físico.
Não há configuração externa nova nem seleção automática de perfil físico.

Aspas simples/duplas, inclusive duplicadas e literais continuados com seu
delimitador em cada registro, protegem o payload. TAB dentro de literal,
comentário `*`/`/`, comentário inline e área de identificação é preservado.
Continuação preserva TAB de payload no fim do fragmento. Indicadores inválidos
continuam rejeitados; a margem 72 é aplicada após a expansão de separadores.
Espaços gerados apontam para o HT original com exact=false; tokens intactos
após HT conservam offsets/colunas físicos exatos, inclusive em COPY aninhado.
O scanner é linear; arquivos sem HT mantêm o caminho e os bytes anteriores.

Esta é uma política de importação do produto, não uma afirmação de que COBOL
IBM define HT de largura oito. Precedente: [IBM z/OS expand](https://www.ibm.com/docs/en/zos/3.1.0?topic=descriptions-expand-expand-tabs-spaces);
áreas e continuação: [IBM indicator area](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=format-indicator-area).
Evidência: `FixedTabNormalizationTest`, mínimos de prefixo e separador SQL,
fronteiras 7/8/72/73, Unicode, LF/CRLF/CR, payload e provenance/COPY.

A inspeção das duas variantes físicas mostrou que paradas de oito colunas
truncavam cláusulas depois de dois HT iniciais. A política de quatro colunas
preserva ambos os prefixos e as cláusulas completas dentro da margem 72.
É uma convenção interna determinística de importação, sem inferência de dialeto
ou promessa de recuperar a intenção de todo arquivo externo. Indicador inválido
após a expansão continua sendo erro; não há seleção por arquivo/corpus.

## Comentários em EXEC CICS

O preprocessor usa os tokens COMMENTLINE para substituir por espaços os trechos
de comentário autorizados antes de achatar os registros do envelope CICS. A
substituição conserva as posições em code points e os segmentos de origem dos
operandos; o fonte físico continua preservado. Conteúdo entre aspas, inclusive
`*>` e aspas duplicadas, não é comentário. Um marcador inline sem separador
anterior não é apagado. O scanner de opções também aceita comentários no payload
não achatado e conserva seus offsets; hosts e labels usam o início do operando
registrado pelo scanner, sem procurar parênteses no texto de comentários.

Autoridade e matriz de aliases FILE/DATASET: [W2](../work/carddemo-control-w2.md).
Evidência: CicsLexicalCompatibilityTest (comentários *, / e flutuantes, LF/CRLF/CR,
Unicode, aspas, offsets, todos os aliases autorizados e negativos), mais as
suítes existentes de normalização, provenance e CICS.

## Continuação entre operandos completos

A continuação com `-` também admite a fronteira entre operandos completos.
O scanner conserva o estado lexical: palavra partida é concatenada; literal
aberto mantém seu delimitador de continuação; literais já fechados permanecem
como tokens separados. Em particular, `'A'` seguido de `'B'` não vira `'A''B'`.
Uma aspa na coluna física 72 seguida por duas aspas correspondentes no próximo
registro representa uma aspa no payload do literal continuado. A coluna é medida
no registro físico, inclusive depois de outra continuação e da expansão de TAB.
Prefixo de literal (X/NX/N/G/Z) e aspa inicial, assim como delimitadores de dois
caracteres (`==`, `*>`, `>>`), não podem ser montados entre registros.

Autoridade: [IBM Enterprise COBOL — continuation lines](https://www.ibm.com/docs/en/cobol-zos/6.5.0?topic=b-continuation-lines).
O tratamento preexistente de registros curtos dentro de literais abertos é
preservado: não se acrescentam espaços implícitos até a coluna 72. Esta mudança
não amplia esse domínio. Registros físicos e terminadores continuam preservados;
o trecho combinado tem origem aproximada, inclusive quando vem de COPY.
Evidência: `SourceNormalizerTest` e
`SourceNormalizationPreprocessingIntegrationTest.completeLiteralsRemainDistinctThroughCopyPreprocessing`.
