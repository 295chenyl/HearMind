import api from './client'

export function getImportHealth() {
  return api.get('/system/import-health')
}
