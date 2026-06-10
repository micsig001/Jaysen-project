<template>
  <router-view />
</template>

<script setup lang="ts">
// Root component
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

/* Mobile optimization */
@media (max-width: 768px) {
  html {
    font-size: 14px;
  }
}
</style>
