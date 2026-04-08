{{/*
=============================================================================
SmartBank — shared template helpers
All named templates defined here are available to every file in templates/
=============================================================================
*/}}

{{/*
Chart label: used in helm.sh/chart label
*/}}
{{- define "smartbank.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Resolve image tag: use service-specific tag, fall back to global.imageTag.
Usage: {{ include "smartbank.imageTag" (dict "svc" .Values.apps.authService "global" .Values.global) }}
*/}}
{{- define "smartbank.imageTag" -}}
{{- if and .svc.image .svc.image.tag .svc.image.tag }}
{{- .svc.image.tag }}
{{- else }}
{{- .global.imageTag }}
{{- end }}
{{- end }}

{{/*
Resolve full image reference.
Usage: {{ include "smartbank.image" (dict "svc" $svc "global" $.Values.global) }}
*/}}
{{- define "smartbank.image" -}}
{{- $tag := include "smartbank.imageTag" . }}
{{- printf "%s/%s:%s" .global.registry .svc.image.repository $tag }}
{{- end }}

{{/*
Common labels — applied to every resource.
*/}}
{{- define "smartbank.labels" -}}
helm.sh/chart: {{ include "smartbank.chart" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: smartbank
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}

{{/*
Selector labels for a named service.
Usage: {{ include "smartbank.selectorLabels" (dict "name" "auth-service" "instance" .Release.Name) }}
*/}}
{{- define "smartbank.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .instance }}
{{- end }}

{{/*
Pod security context (shared across all pods).
*/}}
{{- define "smartbank.podSecurityContext" -}}
{{- with .Values.global.podSecurityContext }}
runAsNonRoot: {{ .runAsNonRoot }}
runAsUser: {{ .runAsUser }}
runAsGroup: {{ .runAsGroup }}
fsGroup: {{ .fsGroup }}
{{- end }}
{{- end }}

{{/*
Container security context (shared across all containers).
*/}}
{{- define "smartbank.containerSecurityContext" -}}
{{- with .Values.global.containerSecurityContext }}
allowPrivilegeEscalation: {{ .allowPrivilegeEscalation }}
readOnlyRootFilesystem: {{ .readOnlyRootFilesystem }}
capabilities:
  drop:
  {{- range .capabilities.drop }}
    - {{ . }}
  {{- end }}
{{- end }}
{{- end }}

{{/*
Standard resource limits (from global).
*/}}
{{- define "smartbank.resources" -}}
{{- with .Values.global.resources }}
requests:
  cpu: {{ .requests.cpu }}
  memory: {{ .requests.memory }}
limits:
  cpu: {{ .limits.cpu }}
  memory: {{ .limits.memory }}
{{- end }}
{{- end }}

{{/*
Pod anti-affinity — requiredDuringScheduling spread across hostnames.
Usage: {{ include "smartbank.podAntiAffinity" (dict "appName" "auth-service") }}
*/}}
{{- define "smartbank.podAntiAffinity" -}}
podAntiAffinity:
  requiredDuringSchedulingIgnoredDuringExecution:
    - labelSelector:
        matchExpressions:
          - key: app.kubernetes.io/name
            operator: In
            values:
              - {{ .appName }}
      topologyKey: kubernetes.io/hostname
{{- end }}

{{/*
Readiness probe for a Spring Boot Actuator endpoint.
Usage: {{ include "smartbank.readinessProbe" (dict "port" 8081 "global" .Values.global) }}
*/}}
{{- define "smartbank.readinessProbe" -}}
httpGet:
  path: /actuator/health/readiness
  port: {{ .port }}
initialDelaySeconds: {{ .global.readinessProbe.initialDelaySeconds }}
periodSeconds: {{ .global.readinessProbe.periodSeconds }}
timeoutSeconds: {{ .global.readinessProbe.timeoutSeconds }}
failureThreshold: {{ .global.readinessProbe.failureThreshold }}
{{- end }}

{{/*
Liveness probe for a Spring Boot Actuator endpoint.
*/}}
{{- define "smartbank.livenessProbe" -}}
httpGet:
  path: /actuator/health/liveness
  port: {{ .port }}
initialDelaySeconds: {{ .global.livenessProbe.initialDelaySeconds }}
periodSeconds: {{ .global.livenessProbe.periodSeconds }}
timeoutSeconds: {{ .global.livenessProbe.timeoutSeconds }}
failureThreshold: {{ .global.livenessProbe.failureThreshold }}
{{- end }}

{{/*
Standard emptyDir volumes for readOnlyRootFilesystem compatibility.
Spring Boot needs /tmp for embedded Tomcat temp files.
*/}}
{{- define "smartbank.volumes" -}}
- name: tmp
  emptyDir: {}
- name: app-logs
  emptyDir: {}
{{- end }}

{{/*
Standard volumeMounts for the above volumes.
*/}}
{{- define "smartbank.volumeMounts" -}}
- name: tmp
  mountPath: /tmp
- name: app-logs
  mountPath: /app/logs
{{- end }}

{{/*
Standard initContainer that waits for config-server + discovery-server.
*/}}
{{- define "smartbank.waitForInfra" -}}
- name: wait-for-infra
  image: curlimages/curl:8.5.0
  securityContext:
    runAsNonRoot: true
    runAsUser: 1000
    allowPrivilegeEscalation: false
    readOnlyRootFilesystem: true
    capabilities:
      drop: [ALL]
  command:
    - sh
    - -c
    - |
      until curl -sf {{ .configServerUrl }}/actuator/health/readiness && \
            curl -sf {{ .discoveryUrl | replace "/eureka/" "" }}/actuator/health/readiness; do
        echo "Waiting for infra services..."; sleep 5
      done
{{- end }}

{{/*
Resolve a secret value: use per-service override if set, otherwise fall back to global.
Usage: {{ include "smartbank.secretVal" (dict "svcVal" $svc.secrets.DB_PASSWORD "globalVal" $.Values.global.secrets.postgresPassword) }}
*/}}
{{- define "smartbank.secretVal" -}}
{{- if .svcVal }}{{ .svcVal }}{{- else }}{{ .globalVal }}{{- end }}
{{- end }}

{{/*
Standard Prometheus annotations for pod scraping.
*/}}
{{- define "smartbank.prometheusAnnotations" -}}
prometheus.io/scrape: "true"
prometheus.io/path: "/actuator/prometheus"
prometheus.io/port: {{ .port | quote }}
{{- end }}
