# Provenance

## Propósito e escopo

Provenance é parte do contrato de análise. O `SourceMap` nasce no arquivo físico antes de normalização, preprocessing ou expansão de COPY e é composto por todas as transformações posteriores.

## Entradas e saídas

- **Entrada:** texto físico e nome do arquivo; depois, operações de slice, replacement e expansão.
- **Saída:** texto corrente, segmentos para arquivos originais, include chains e flag de exatidão.

## Regras atuais

- Conteúdo não transformado e seus terminadores de linha mantêm segmentos exatos.
- Conteúdo normalizado, expandido ou reescrito mantém a origem, mas é marcado como aproximado quando não há correspondência física exata.
- Nenhuma etapa pode criar um mapa de identidade sobre o texto já transformado para simplificar posições.
- COPYs, inclusive aninhados, preservam origem e cadeia de inclusão.
- Diagnostics, nós semânticos e occurrences que declararem origem devem apontar para localização válida e coerente com o `SourceMap`.
- Offsets e colunas são expressos em code points Unicode; conversões para
  unidades UTF-16 ficam restritas ao acesso às APIs de `String`.

Uma perda de input, COPY ausente ou construção opaca continua visível na cobertura e nos diagnostics. Não é permitido converter perda de provenance em ausência de efeito semântico.

## Fronteiras e complexidade

O parser pode oferecer posições de token, mas elas não substituem a abstração
de provenance. Source format, normalização e preprocessing são responsáveis por
compor o mapa; AST, símbolos e resolução o consomem sem reconstruí-lo. Segmentos
adjacentes compatíveis são mesclados; índices de segmentos e linhas permitem
localizar por busca binária somente o intervalo solicitado.

## Evidência executável

`SourceProvenanceTest`, `SourceNormalizerTest`, `SourceNormalizationPreprocessingIntegrationTest` e o cenário E2E do normalizador.

## Relações

Evals: EVAL-PROV-001, EVAL-PROV-002 e EVAL-PRE-002. Invariantes: INV-PROV-001, INV-PROV-002 e INV-COV-001. ADR: ADR-0002.

A fronteira física do source principal é conservada separadamente dos segmentos de
texto. Ela permite qualificar ownership EOF conforme o contrato de compilation units;
não acrescenta token, linha ou flag exact. Substituições no documento preservam a
fronteira principal; fragments/inclusões isolados não a estabelecem.

## Artefatos de modelo nominal

O catálogo DFH usa uma identidade versionada `model:ibm-cics-ts/nominal-v1/`.
As coordenadas originais apontam para as declarações geradas desse modelo; a
cadeia de COPY aponta para o pedido no fonte do usuário. Exatidão significa
correspondência com o texto do modelo, sem afirmar equivalência ao membro IBM.
O diagnostic tipado NOMINAL_COPYBOOK e a região de input parcial acompanham a
expansão. Nomes resolvidos não autorizam valores ou storage conhecidos.

## Retained operands inside embedded framing

DLI framing keeps the normalized payload byte-for-byte, including line endings,
and carries its retained SourceMap segments through the framing and COPY chain.
COPY REPLACING composes both the ordinary and retained maps; a replacement cannot
fall back to the enclosing command's coarse origin. The whole-command origin stays
unchanged and operand `exact` stays false. No value, storage or control proof is
created by this coordinate refinement. `ExecDliProvenanceTest` covers original
operand slices, nested COPY/REPLACING, Unicode, line endings and following sentinels.
