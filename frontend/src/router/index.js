import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn } from '../utils/auth'
import UploadView from '../views/UploadView.vue'
import VideoListView from '../views/VideoListView.vue'
import VideoDetailView from '../views/VideoDetailView.vue'
import LoginView from '../views/LoginView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/', name: 'upload', component: UploadView },
    { path: '/videos', name: 'videos', component: VideoListView },
    { path: '/videos/:id', name: 'video-detail', component: VideoDetailView }
  ]
})

router.beforeEach((to) => {
  if (to.meta.public) {
    return true
  }
  if (!isLoggedIn()) {
    return '/login'
  }
  return true
})

export default router
