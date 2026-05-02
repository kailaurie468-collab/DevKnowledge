import { createContext, useContext } from 'react'

export const ParticleContext = createContext({
  visible: true,
  setVisible: (_v: boolean) => {},
})

export function useParticleVisible() {
  return useContext(ParticleContext)
}
