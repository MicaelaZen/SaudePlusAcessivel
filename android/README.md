# Cuidar+ Acessível para Android

Esta versão hospeda a interface web em um WebView Android e agenda lembretes diários no próprio aparelho. Os alarmes não dependem de servidor ou de a tela do app permanecer aberta.

## Requisitos

- Android Studio com Android SDK Platform 35 e Android SDK Build-Tools instalados.
- JDK 17 ou mais recente.
- Gradle 8.11.1 para gerar o wrapper; o Android Gradle Plugin está fixado em 8.9.2.

## Gerar e instalar o APK de depuração

1. Instale o Android Studio e, pelo SDK Manager, instale Android SDK Platform 35 e Build-Tools 35.
2. Abra um terminal nesta pasta (`android`) com JDK 17 ativo e execute:

```powershell
gradle wrapper --gradle-version 8.11.1
```

3. Gere o APK:

```powershell
.\gradlew.bat assembleDebug
```

O APK será criado em `app/build/outputs/apk/debug/app-debug.apk`. Abra-o em um aparelho Android para instalar.

## Ativar um lembrete

1. Abra **Medicamentos**, cadastre o medicamento e informe o horário.
2. Marque **Receber alerta diário mesmo com o app fechado neste aparelho** e salve.
3. Autorize as notificações. Quando solicitado, permita também **Alarmes e lembretes** nas configurações do Android.

Os lembretes são diários e locais. Eles são restaurados após reiniciar o aparelho e cancelados ao excluir o medicamento. A notificação é genérica para não expor o nome do medicamento na tela bloqueada. O navegador continua oferecendo somente lembretes enquanto a página estiver aberta.

O horário exato depende da permissão especial de alarmes do Android. Se ela ou as notificações estiverem desativadas, o sistema não pode garantir a entrega.

Fechar o app normalmente não cancela o alarme. Usar **Forçar parada** nas configurações do Android suspende a entrega até abrir o app novamente; alguns fabricantes também podem aplicar restrições próprias de bateria.

## Lembretes e Google Agenda

Ao cadastrar uma consulta, informe especialidade, nome do médico, data e hora. Escolha sem lembrete, 5, 15 ou 30 minutos, 1 hora ou 1 dia antes. No Android, o lembrete é um alarme local pontual, funciona com o app fechado, é restaurado após reiniciar o aparelho e é cancelado ao excluir a consulta. A notificação é genérica para manter médico e local privados na tela bloqueada. No navegador, o aviso depende de a página estar aberta.

O perfil também pode guardar e-mail e celular. **Adicionar ao Google Agenda** abre um evento pré-preenchido e usa o e-mail do perfil como participante. A pessoa precisa entrar na conta Google e confirmar o evento; pode configurar as notificações do evento na tela do Google Agenda. O lembrete local do Cuidar+ continua independente. Este link não concede acesso OAuth nem grava eventos automaticamente; integração automática exigiria configuração OAuth e consentimento Google.

## Glicemia capilar

Na área **Glicemia**, registre o resultado em mg/dL, data, horário e contexto. Em **Editar**, configure os limites inferior e superior informados pela equipe de saúde. Não há valores padrão; os limites podem ser revistos pelo usuário ou pelo cuidador sempre que houver nova orientação. Também é possível desativar os alertas sem excluir o histórico.

Uma leitura fora dos limites configurados gera aviso no app e, no Android, uma notificação local genérica. O alerta não recomenda ações clínicas nem mostra o resultado. Os registros e limites ficam no armazenamento local do WebView e não são enviados a um servidor. A indicação de quem ajustou os limites é autodeclarada; esta versão ainda não autentica cuidadores.

## Pressão arterial

Na área **Pressão arterial**, registre os valores sistólico e diastólico em mmHg, data, horário, contexto e pulso opcional. Configure os limites mínimos e máximos para ambos os valores com orientação da equipe de saúde; não há faixas padrão. O usuário pode rever ou desativar os alertas sem apagar o histórico. Valores fora da faixa configurada geram aviso no app e notificação Android genérica, sem exibir a leitura.