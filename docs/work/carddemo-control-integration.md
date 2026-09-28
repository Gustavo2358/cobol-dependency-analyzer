# CardDemo — integração de FILE e W0–W8

Status: DONE — implementação de `proleap-poc` qualificada e mergeada em 28/09/2026, após autorização do usuário. Este fechamento documental não altera produção, testes, schemas ou oracles.

## Entrega

A campanha preserva dependências sob controle desconhecido e corrige composição FILE, comentários/aliases CICS, separação de controle e memória, áreas BMS, famílias CICS, SQL/DL/I e controle COBOL. W7 publica a política de reentrada indefinida e conserva possibilidades fonte condicionais; W8 qualifica preservação e corrige o anúncio da versão dependencies 2.7.

Os commits progressivos foram preservados por merges normais em `main`. Antes e depois de cada merge, a árvore completa foi comparada com o head qualificado do PR: igualdade em todos os merges deste repositório. Head final de revisão: `afd29b423efa2be0fd896519ed548bfe424dc851`. Merge final de implementação: `79ea0a6d9469f6ab2211ef1cc33d0c7e2d4a75d3`.

| PR | Commit de merge |
| --- | --- |
| [#66](https://github.com/Gustavo2358/proleap-poc/pull/66) | `1c4285a664bbc8014119d564735a27f5ede03953` |
| [#67](https://github.com/Gustavo2358/proleap-poc/pull/67) | `43d94419b730722297ca9f9440ad1f6b54145943` |
| [#68](https://github.com/Gustavo2358/proleap-poc/pull/68) | `696b267d571e8bef5a9e1a6b3ff6b1bdc9ac3b3b` |
| [#69](https://github.com/Gustavo2358/proleap-poc/pull/69) | `699fe16a1baf03dd04d68454ed241b5478cda8e3` |
| [#70](https://github.com/Gustavo2358/proleap-poc/pull/70) | `4e07e56aa0533b79972c4e0f92bb80fab2fc697e` |
| [#71](https://github.com/Gustavo2358/proleap-poc/pull/71) | `93b7beec8d46ba222af5a3fcadde289539c5e198` |
| [#72](https://github.com/Gustavo2358/proleap-poc/pull/72) | `f4b79781fcb5645c3c4d0a9a16db1ac0dd9b4d25` |
| [#73](https://github.com/Gustavo2358/proleap-poc/pull/73) | `79ea0a6d9469f6ab2211ef1cc33d0c7e2d4a75d3` |

## Qualificação preservada

[Relatório W7/W8](carddemo-control-w7-w8.md) e [censo de todos os CardDemo](carddemo-control-w7-w8-carddemo.csv): 560/560 entradas nas quatro etapas, 73/73 CardDemo, PERFORM 39/39, Chaos 48/48, aliases 14/14 e PERFORM adversarial 25/25. W7 focal: 22 casos e 110 mutações de contrato. Zero perda de candidato ou support físico; 109 supports condicionais preservados; dois acréscimos condicionais investigados em fixtures de reentrada indefinida.

Frontend FAST 648 e full 1.222 testes (um skip opt-in preexistente); lower FAST/full e CFG FAST de 640 métodos passaram na qualificação W8. Estes são resultados reutilizados, não novas execuções desta alteração documental. Produção, recursos, testes, contratos e configuração de build permanecem iguais aos heads qualificados; somente documentação e pins equivalentes mudam no fechamento. Por isso o corpus/full não é repetido. Os gates de integração e os SHAs finais ficam registrados no PR documental, nos checks de `main` e no relatório E2E local `carddemo-control-integration-20260928/REPORT.md`.

## Autoridade e limites

SP 2.57, qualified-source-dependencies 1.2 e dependencies 2.7 permanecem os contratos vigentes. Pins downstream devem apontar para merges reais, na ordem frontend → lower → CFG. Os estados históricos dos relatórios não substituem esta integração.

- Cobertura continua PARTIAL; execução com exit zero não significa análise completa.
- Nenhuma aresta executável foi fabricada para conectar componentes. Todos os 73 grafos W7 são iguais aos W6 após normalizar somente o namespace da publicação.
- Reentrada ativa do mesmo PERFORM continua sem retorno executável definido sob IBM Enterprise COBOL 6.4. W7 preserva possibilidades condicionais fonte; não cumpre a premissa original de implementar recursão executável.
- Expansão de contextos AIR ainda pode ser exponencial. Compartilhar corpos com contexto de retorno preciso é uma campanha separada.
- Valores externos/desconhecidos e cinco targets indexados de menus continuam limites. Modelos IBM não concedem valor de runtime, layout físico ou kill forte.
- Full histórico do CFG, full AIR/IR e UI não foram executados nesta integração. AIR/IR não mudaram.

## Próximo trabalho

O usuário separou o encerramento desta campanha das próximas: os pontos 1–4 (MOVE com múltiplos destinos, targets indexados, funções/registradores e controle ainda não admitido) precisam de escopo e discovery próprios. O ponto 5 (representação compartilhada de rotinas e contexto) fica em outra campanha. Nenhuma dessas implementações faz parte deste fechamento. ALTER permanece fora do escopo.
