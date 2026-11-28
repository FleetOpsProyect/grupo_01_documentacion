# Despliegue en AWS del servicio de Documentación y Seguros

Guía para dejar el servicio funcionando en AWS con **todas sus capacidades activas**:

- consumidores SQS de las réplicas de vehículos y conductores (HU-DOC-10);
- publicador del outbox en SNS;
- job de vigencia (HU-DOC-06);
- despliegue automático al integrar a `main`.

Cuenta `651147260933`, región `us-east-2`, instancia `i-04333b1059b6e55cd`.

> **Región.** Todo se crea en la región seleccionada del proyecto. Confírmela en **AWS Settings → View all projects → Overview → Additional Info → Region** antes de empezar. Si no puede verla allí, revise `~/.aws/config`. No cree recursos en otra región.

---

## Cómo obtiene el servicio sus credenciales de AWS

**Decisión: rol de IAM asociado a la EC2 (perfil de instancia).** No hay llaves en ningún archivo.

```
GitHub Actions ──llaves actuales del environment──▶ SSM SendCommand ──▶ EC2 i-04333b1059b6e55cd
                                                                     │  docker compose
                                                                     ▼
                                       contenedor documents-service ──IMDSv2──▶ rol Ec2Role
                                                                     │  (credenciales temporales, rotan solas)
                                                                     ├──▶ SNS fleetops_document_events (publica)
                                                                     └──▶ SQS 2 colas propias (consume)
```

| Opción | Por qué |
|---|---|
| ✅ **Rol de la EC2** | El SDK de AWS pide credenciales temporales al servicio de metadatos de la instancia (IMDSv2) y AWS las rota cada pocas horas. Nada que guardar, filtrar ni rotar a mano. El perfil `aws` del servicio ya usa la cadena de credenciales por defecto del SDK. |
| ❌ Llaves de acceso en el `.env` | Son de larga duración. Si se filtran (un backup, un `cat` en un log), sirven desde cualquier lugar hasta que alguien las revoque. |
| ❌ Tokens temporales manuales | Caducan y el servicio se detiene hasta que alguien los renueve. |
| ❌ IAM Roles Anywhere | Es para cargas **fuera** de AWS; esta ya corre en EC2. |

**GitHub Actions** sigue desplegando exactamente como hoy: `deploy.yml` no cambia y usa las llaves que ya tiene el environment `documents-production` (`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`). El stack no crea nada para GitHub. El paso 6 (opcional) reduce el riesgo de esas llaves con permisos mínimos y rotación.

---

## Qué crea la plantilla `documents-stack.yaml`

| Recurso | Para qué |
|---|---|
| Tópico SNS `fleetops_document_events` (cifrado con `aws/sns`) | Donde el servicio publica sus eventos |
| Tópicos `fleetops_driver_events` y `fleetops_vehicle_events` | **Solo si no se pasa su ARN**: provisionales, hasta que Conductores y Vehículos tengan los suyos en la cuenta |
| Colas `fleetops_document_driver_events_queue` y `fleetops_document_vehicle_events_queue`, con su DLQ | Long polling de 20 s, cifrado SSE-SQS, redrive a la DLQ tras 3 recepciones |
| Políticas de las colas | Solo el tópico del productor puede escribir (`aws:SourceArn` y `aws:SourceAccount`); se rechaza el tráfico sin TLS |
| Suscripciones | Raw message delivery y FilterPolicy **solo** sobre el Message Attribute `event_type` |
| Rol `<stack>-ec2` y su perfil de instancia | SQS (recibir y borrar en las 2 colas), SNS (publicar en el tópico propio), KMS (solo a través de SNS) y `AmazonSSMManagedInstanceCore` |
| 2 alarmas de CloudWatch y, opcionalmente, un tópico con correo | Aviso cuando llega un mensaje a una DLQ |

Lo que **no** puede hacer CloudFormation, porque la EC2 ya existe y no pertenece al stack, se hace a mano en los pasos 3 a 5.

---

## Paso 0. Revisar el estado actual de la cuenta (y compartir el resultado)

Necesita una sesión de la AWS CLI con permisos de lectura. Si los comandos responden «Unable to locate credentials», ejecute `aws login` (o configure su perfil) y repita.

Ejecute los comandos y **comparta la salida** para ajustar los parámetros. Ninguno modifica nada.

```bash
# 1) Identidad y región: la cuenta debe ser 651147260933
aws sts get-caller-identity
aws configure get region

# 2) Plan de la cuenta (FREE o PAID): define qué servicios están disponibles
aws freetier get-account-plan-state --region us-east-2

# 3) La instancia: arquitectura, perfil de IAM actual y opciones de metadatos
aws ec2 describe-instances --region us-east-2 --instance-ids i-04333b1059b6e55cd \
  --query "Reservations[0].Instances[0].{Estado:State.Name,Arquitectura:Architecture,PerfilIAM:IamInstanceProfile.Arn,IMDS:MetadataOptions}"

# 4) ¿SSM ve la instancia? (el despliegue depende de esto)
aws ssm describe-instance-information --region us-east-2 \
  --filters Key=InstanceIds,Values=i-04333b1059b6e55cd \
  --query "InstanceInformationList[0].{Ping:PingStatus,Agente:AgentVersion}"

# 5) ¿Ya existen tópicos o colas de FleetOps? (Conductores y Vehículos podrían tener los suyos)
aws sns list-topics --region us-east-2 --query "Topics[?contains(TopicArn, 'fleetops')]"
aws sqs list-queues --region us-east-2 --queue-name-prefix fleetops
```

Si el comando 3 muestra un perfil de IAM, revise también sus permisos:
```bash
aws iam list-attached-role-policies --role-name <nombre del rol del perfil>
aws iam list-role-policies --role-name <nombre del rol del perfil>
```

**En la consola web:**
- Plan y límite de gasto: **AWS Settings → Billing**.
- Instancia: **EC2 → Instances → i-04333b1059b6e55cd → pestaña Details** (campos *IAM Role* y *Instance metadata*).
- SSM: **Systems Manager → Fleet Manager**; la instancia debe estar *Online*.

> Si el plan es **FREE**, verifique que SNS, SQS, CloudWatch e IAM aparezcan en la [lista de servicios del plan gratuito](https://docs.aws.amazon.com/accounts/latest/reference/supported-services-sign-up-new.html#supported-services-free-tier). Si el plan es **PAID**, use la [lista del plan pago](https://docs.aws.amazon.com/accounts/latest/reference/supported-services-sign-up-new.html#supported-services-paid-plan).

### Resultado de la revisión (27-11-2026)

| Revisión | Resultado | Consecuencia |
|---|---|---|
| Plan de la cuenta | **FREE**, activo, 118,94 USD de crédito, vence el 27-04-2027 | Todos los servicios del stack (SNS, SQS, CloudWatch, CloudFormation, IAM, STS, KMS, Systems Manager, EC2) están en la lista del plan gratuito y ninguno en la de no soportados. El costo esperado es prácticamente 0 (ver «Costos»). Antes del vencimiento hay que pasar al plan pago para que el proyecto siga activo |
| Perfil de IAM de la instancia | Existe, solo con `AmazonSSMManagedInstanceCore` | El rol nuevo incluye esa misma política: **reemplazar el perfil (paso 3) no quita ningún permiso** |
| Tópicos y colas de FleetOps | No existen | `DriverTopicArn` y `VehicleTopicArn` **vacíos**: el stack crea los tópicos provisionales de Conductores y Vehículos |

---

## Paso 1. Validar la plantilla (opcional, recomendado)

```bash
# Validación local de sintaxis, esquema y propiedades
pip install cfn-lint
cfn-lint infra/aws/documents-stack.yaml

# Validación del servicio CloudFormation (no crea nada)
aws cloudformation validate-template --region us-east-2 --template-body file://infra/aws/documents-stack.yaml
```

---

## Paso 2. Crear el stack

**Consola web:**
1. Abra **CloudFormation** y compruebe arriba a la derecha que la región sea **US East (Ohio) us-east-2**.
2. **Create stack → With new resources (standard)**.
3. *Choose an existing template → Upload a template file*, elija `infra/aws/documents-stack.yaml` → **Next**.
4. *Stack name*: `fleetops-documents`. El rol y el perfil de instancia se llamarán `fleetops-documents-ec2`.
5. Parámetros:

   | Parámetro | Valor |
   |---|---|
   | `DriverTopicArn` / `VehicleTopicArn` | **Vacíos**: hoy no existen en la cuenta (paso 0) |
   | `QueueVisibilityTimeoutSeconds` | `30` |
   | `AlarmEmail` | Su correo (opcional) |

6. **Next**, agregue la etiqueta `project = fleetops` (opcional) y, al final, marque **«I acknowledge that AWS CloudFormation might create IAM resources with custom names»**.
7. **Submit**. Espere `CREATE_COMPLETE`, unos 2 minutos, en la pestaña **Events**.
8. En la pestaña **Outputs**, copie `DocumentTopicArn` y `Ec2InstanceProfileName`.

**CLI equivalente:**
```bash
aws cloudformation deploy --region us-east-2 \
  --stack-name fleetops-documents \
  --template-file infra/aws/documents-stack.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides AlarmEmail=<su correo>

aws cloudformation describe-stacks --region us-east-2 --stack-name fleetops-documents \
  --query "Stacks[0].Outputs" --output table
```

Si dio un correo, confirme la suscripción con el enlace que le llega de **AWS Notifications**.

---

## Paso 3. Asociar el rol a la EC2 (manual)

Una instancia tiene **un solo** perfil de IAM. El perfil nuevo incluye `AmazonSSMManagedInstanceCore`, así que el despliegue por SSM sigue funcionando.

> **En esta cuenta:** el perfil actual solo tiene `AmazonSSMManagedInstanceCore`, que el rol nuevo también incluye. Al reemplazarlo no se pierde ningún permiso y el despliegue por SSM sigue funcionando. El rol anterior queda sin uso; puede borrarlo más adelante desde **IAM → Roles**, una vez confirmado que todo funciona.

**Consola web:** **EC2 → Instances →** seleccione `i-04333b1059b6e55cd` **→ Actions → Security → Modify IAM role →** elija `fleetops-documents-ec2` **→ Update IAM role**.

**CLI:**
```bash
# Sin perfil actual:
aws ec2 associate-iam-instance-profile --region us-east-2 --instance-id i-04333b1059b6e55cd \
  --iam-instance-profile Name=fleetops-documents-ec2

# Con perfil actual: reemplazarlo
ASSOC=$(aws ec2 describe-iam-instance-profile-associations --region us-east-2 \
  --filters Name=instance-id,Values=i-04333b1059b6e55cd --query "IamInstanceProfileAssociations[0].AssociationId" --output text)
aws ec2 replace-iam-instance-profile-association --region us-east-2 --association-id "$ASSOC" \
  --iam-instance-profile Name=fleetops-documents-ec2
```

Espere uno o dos minutos y compruebe en **Systems Manager → Fleet Manager** que la instancia siga **Online**.

---

## Paso 4. Opciones de metadatos de la instancia (manual)

El servicio corre en un contenedor con red *bridge*, así que llega al servicio de metadatos con **un salto de red más**. Con el límite por defecto (1), el SDK no obtiene las credenciales y el servicio falla con «Unable to load credentials».

**Consola web:** **EC2 → Instances →** seleccione la instancia **→ Actions → Instance settings → Modify instance metadata options**:
- *Instance metadata service*: **Enable**
- *IMDSv2*: **Required** (más seguro que permitir IMDSv1)
- *Metadata response hop limit*: **2**
- **Save**.

**CLI:**
```bash
aws ec2 modify-instance-metadata-options --region us-east-2 --instance-id i-04333b1059b6e55cd \
  --http-tokens required --http-put-response-hop-limit 2 --http-endpoint enabled
```

---

## Paso 5. Variables del servidor (manual)

El despliegue usa `/opt/fleetops/documents/.env`, que vive solo en el servidor.

**Consola web:** **EC2 → Instances →** seleccione la instancia **→ Connect → pestaña Session Manager → Connect**. En la terminal que se abre:

```bash
sudo -i
cd /opt/fleetops/documents
cp .env .env.bak-$(date +%Y%m%d)          # respaldo
nano .env
```

Agregue o ajuste estas líneas. `MESSAGING_DOCUMENT_TOPIC_ARN` es el *Output* `DocumentTopicArn` del paso 2.

```properties
SPRING_PROFILES_ACTIVE=aws
AWS_REGION=us-east-2
MESSAGING_WAIT_TIME_SECONDS=20
MESSAGING_MAX_MESSAGES=10
MESSAGING_RETRY_DELAY=5s
MESSAGING_DOCUMENT_TOPIC_ARN=arn:aws:sns:us-east-2:651147260933:fleetops_document_events
```

**No defina** `MESSAGING_ENDPOINT`, `MESSAGING_CONTAINER_ENDPOINT`, `MESSAGING_ACCESS_KEY_ID` ni `MESSAGING_SECRET_ACCESS_KEY`: son solo del emulador local.

**Eventos acumulados (decisión D-4).** Al activar el perfil `aws`, el publicador envía en orden todos los eventos que quedaron en PENDING mientras el servicio corrió sin mensajería. Incluye alertas de vencimiento antiguas, que Notificaciones recibirá. Para ver cuántos hay:

```bash
cd /opt/fleetops/documents/app
docker compose exec documents-db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT event_type, count(*) FROM document.outbox_events WHERE status = '"'"'PENDING'"'"' GROUP BY event_type"'
```

---

## Paso 6. GitHub: endurecer las llaves de despliegue (opcional, recomendado)

El despliegue no requiere cambios en GitHub: `deploy.yml` y las variables y secretos del environment `documents-production` (`AWS_REGION`, `EC2_INSTANCE_ID`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`) quedan como están. Si quiere reducir el riesgo de las llaves, aplique estas dos medidas.

**1. Permisos mínimos para el usuario de las llaves.** Si alguien obtiene las llaves, solo podrá enviar el comando de despliegue a esta instancia y leer su resultado.

1. Ubique el usuario dueño de las llaves: **IAM → Users →** busque el que tenga una *Access key* cuyo ID coincida con el que guardó en GitHub (los primeros caracteres, `AKIA…`, se ven en la pestaña **Security credentials**).
2. En la pestaña **Permissions**, revise qué políticas tiene. Si tiene políticas amplias (por ejemplo `AdministratorAccess` o `AmazonSSMFullAccess`), **confirme antes que el usuario no se use para otra cosa**.
3. **Add permissions → Create inline policy → JSON**, pegue esta política y guárdela con el nombre `documents-deploy-by-ssm`:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Sid": "SendDeployCommand",
         "Effect": "Allow",
         "Action": "ssm:SendCommand",
         "Resource": [
           "arn:aws:ec2:us-east-2:651147260933:instance/i-04333b1059b6e55cd",
           "arn:aws:ssm:us-east-2::document/AWS-RunShellScript"
         ]
       },
       {
         "Sid": "ReadDeployResult",
         "Effect": "Allow",
         "Action": "ssm:GetCommandInvocation",
         "Resource": "*"
       }
     ]
   }
   ```
   `ssm:GetCommandInvocation` no admite permisos por recurso; por eso lleva `*`.
4. Pruebe el despliegue: **GitHub → Actions → Deploy documents-service to EC2 → Run workflow** (rama `main`). Si termina bien, quite las políticas amplias del paso 2, que el despliegue ya no necesita, y vuelva a probar.

**2. Rotación periódica (cada 90 días, recomendado):**
1. **IAM → Users →** el usuario **→ Security credentials → Create access key** (*Use case: Third-party service*). Copie las dos partes; el secreto solo se muestra una vez.
2. En **GitHub → Settings → Environments → `documents-production` → Environment secrets**, actualice `AWS_ACCESS_KEY_ID` y `AWS_SECRET_ACCESS_KEY`.
3. Ejecute el workflow a mano para confirmar que funciona.
4. En IAM, **Deactivate** la llave anterior y, tras unos días sin errores, **Delete**.

Nunca copie estas llaves en el `.env` del servidor ni en el repositorio: el servicio no las necesita, porque usa el rol de la EC2.

---

## Paso 7. Integrar a `main` y verificar

Con los pasos 2 a 5 hechos (el 6 es opcional), **integre a `main`**. El workflow construye la imagen, la despliega por SSM y espera a que `/health` responda.

**Verificación** (desde Session Manager, en `/opt/fleetops/documents/app`):

```bash
# 1) Perfil activo, consumidores y relevo del outbox iniciados
docker compose logs documents-service | grep -E "profile is active|Consumidor SQS iniciado|Relevo del outbox iniciado|Outbox:"

# 2) Sin errores de credenciales
docker compose logs documents-service | grep -iE "credential|AccessDenied|KMS" || echo "sin errores de credenciales"

# 3) El acumulado se publicó: no debería quedar nada en PENDING ni en FAILED
docker compose exec documents-db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT status, count(*) FROM document.outbox_events GROUP BY status"'
```

**Prueba de punta a punta del consumo.** Requiere permiso `sns:Publish` en el tópico de Conductores. Si el tópico es el provisional del stack, hágalo con su usuario de consola, no desde la EC2:

```bash
aws sns publish --region us-east-2 \
  --topic-arn arn:aws:sns:us-east-2:651147260933:fleetops_driver_events \
  --message '{"event_id":"019a5f00-0000-7000-8000-00000000d001","event_type":"driver_activated_event","occurred_at":"2026-11-27T09:00:00-05:00","source":"driver","data":{"driver_id":"01960000-0000-7000-8000-000000000999","full_name":"Conductor de prueba AWS"}}' \
  --message-attributes '{"event_type":{"DataType":"String","StringValue":"driver_activated_event"}}'
```

A los pocos segundos, el conductor debe aparecer en `document.driver_replica` y el log debe mostrar `Evento driver_activated_event … APPLIED`.

**En la consola:** **SQS →** las DLQ deben tener 0 mensajes; **CloudWatch → Alarms →** las dos alarmas en *OK*.

---

## Vuelta atrás

- **Apagar la mensajería sin desplegar:** en el `.env` del servidor deje `SPRING_PROFILES_ACTIVE=` vacío y ejecute `docker compose up -d`. Los eventos vuelven a esperar en el outbox.
- **Borrar el stack:** los tópicos y las colas tienen `DeletionPolicy: RetainExceptOnCreate`, así que **se conservan**, porque otros equipos y mensajes dependen de ellos. Se borran los roles, las políticas, las suscripciones y las alarmas. Antes, desasocie el perfil de la EC2 (paso 3), o la instancia se queda sin rol.

## Costos

| Recurso | Costo esperado |
|---|---|
| SNS y SQS | Dentro de la capa gratuita para este volumen: 1 M de solicitudes al mes en cada servicio. El long polling de 20 s en 2 colas genera unas 260 000 solicitudes al mes, aunque no haya mensajes |
| KMS (`aws/sns`) | La llave administrada no tiene costo mensual; las solicitudes son mínimas porque SNS reutiliza las llaves de datos |
| CloudWatch | 2 alarmas, dentro de las 10 gratuitas |
| IAM | Sin costo |

Si de pronto aparecen errores **Access Denied** en operaciones que antes funcionaban, revise si el proyecto tiene un **límite de gasto** que lo pausó: **AWS Settings → Billing**. Solo el dueño del proyecto puede modificarlo.

## Problemas frecuentes

| Síntoma en el log | Causa | Solución |
|---|---|---|
| `Unable to load credentials from any of the providers` | Límite de saltos de IMDS en 1, o la EC2 sin perfil | Pasos 3 y 4 |
| `AccessDenied` en `sqs:ReceiveMessage` o `sns:Publish` | Perfil viejo asociado, nombre de cola o tópico distinto, o límite de gasto o SCP del proyecto | Paso 3; revise los *Outputs*; AWS Settings → Billing |
| `KMSAccessDenied` al publicar | El rol no puede usar `aws/sns` | Verifique la política `publish-document-events` del rol |
| `QueueDoesNotExist` | Región distinta en `AWS_REGION`, o el stack no se creó | `AWS_REGION=us-east-2`; paso 2 |
| `MESSAGING_DOCUMENT_TOPIC_ARN debe ser el ARN de un tópico SNS` (no arranca) | Falta la variable o tiene un error | Paso 5 |
| El workflow falla en *Desplegar por SSM* con `AccessDenied` | Las llaves de GitHub no tienen `ssm:SendCommand` sobre la instancia (por ejemplo, tras el paso 6) | Revise la política `documents-deploy-by-ssm` del paso 6 |

## Notas para Conductores y Vehículos

Si el stack creó los tópicos provisionales `fleetops_driver_events` y `fleetops_vehicle_events`:
- Están cifrados con `aws/sns`. Para publicar, el productor necesita `sns:Publish` sobre el tópico, y `kms:GenerateDataKey*` y `kms:Decrypt` con la condición `kms:ViaService = sns.us-east-2.amazonaws.com`.
- Deben publicar con el Message Attribute `event_type`; los filtros de las suscripciones solo miran ese atributo.
- Cuando ese equipo cree su propio tópico, actualice el stack con su ARN en `DriverTopicArn` o `VehicleTopicArn`. El tópico provisional queda conservado y se puede borrar a mano cuando nadie lo use.
