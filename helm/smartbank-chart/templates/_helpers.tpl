{{/*
Expand the name of the chart.
*/}}
{{- define "smartbank.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "smartbank.labels" -}}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Selector label for a given component
*/}}
{{- define "smartbank.selectorLabels" -}}
app.kubernetes.io/name: {{ . }}
{{- end }}

{{/*
Full component labels
*/}}
{{- define "smartbank.componentLabels" -}}
app.kubernetes.io/name: {{ . }}
helm.sh/chart: smartbank
app.kubernetes.io/managed-by: Helm
{{- end }}

{{/*
Spring microservice common env vars (Eureka + Config Server)
*/}}
{{- define "smartbank.springEnv" -}}
- name: EUREKA_PASSWORD
  valueFrom:
    secretKeyRef:
      name: smartbank-secret
      key: EUREKA_PASSWORD
- name: EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
  value: http://eureka:$(EUREKA_PASSWORD)@discovery-server:8761/eureka/
- name: CONFIG_SERVER_PASSWORD
  valueFrom:
    secretKeyRef:
      name: smartbank-secret
      key: CONFIG_SERVER_PASSWORD
- name: SPRING_CLOUD_CONFIG_URI
  value: http://config:$(CONFIG_SERVER_PASSWORD)@config-server:8888
{{- end }}

{{/*
Spring microservice envFrom (configmap + secret)
*/}}
{{- define "smartbank.envFrom" -}}
- configMapRef:
    name: smartbank-config
- secretRef:
    name: smartbank-secret
{{- end }}

{{/*
Business service probes
*/}}
{{- define "smartbank.businessProbes" -}}
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: http
  initialDelaySeconds: 35
  periodSeconds: 10
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: http
  initialDelaySeconds: 70
  periodSeconds: 20
startupProbe:
  httpGet:
    path: /actuator/health
    port: http
  failureThreshold: 36
  periodSeconds: 10
{{- end }}
