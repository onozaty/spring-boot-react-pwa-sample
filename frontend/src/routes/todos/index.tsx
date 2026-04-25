import { createFileRoute } from '@tanstack/react-router'
import { TodoList } from '@/components/todo-list'

export const Route = createFileRoute('/todos/')({
  component: TodosPage,
})

function TodosPage() {
  return (
    <div className="max-w-2xl mx-auto p-8">
      <h1 className="text-3xl font-bold mb-8">TODO</h1>
      <TodoList />
    </div>
  )
}
