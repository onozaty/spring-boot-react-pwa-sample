import { describe, expect, it, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { IDBFactory } from 'fake-indexeddb'
import { server } from '@/test/server'
import { mockTodos } from '@/test/handlers'
import { renderRoute } from '@/test/test-utils'
import type { components } from '@/generated/api'

type Todo = components['schemas']['Todo']

// コンポーネントテストでは vi.resetModules() が使えないため、
// IDBFactory を差し替えるだけで IndexedDB の内容をリセットする。
// fetchAndSyncTodos は API → IDB の順で動くため、
// MSW で GET レスポンスを制御することでテスト間の干渉を防ぐ。
beforeEach(() => {
  // eslint-disable-next-line no-global-assign
  indexedDB = new IDBFactory()
})

describe('TodoList', () => {
  it('TODO一覧が表示される', async () => {
    // Act
    renderRoute({ initialEntries: ['/todos'] })

    // Assert
    await waitFor(() => {
      expect(screen.getByText('牛乳を買う')).toBeInTheDocument()
      expect(screen.getByText('メールを返信する')).toBeInTheDocument()
    })
  })

  it('完了済みのTODOはチェックボックスが選択状態で表示される', async () => {
    // Act
    renderRoute({ initialEntries: ['/todos'] })
    await waitFor(() =>
      expect(screen.getByText('牛乳を買う')).toBeInTheDocument(),
    )

    // Assert — id 降順なので [0]=id2「メールを返信する」(done=true)、[1]=id1「牛乳を買う」(done=false)
    const checkboxes = screen.getAllByRole('checkbox')
    expect(checkboxes[0]).toBeChecked()
    expect(checkboxes[1]).not.toBeChecked()
  })

  it('TODO が空のとき「TODOがありません」が表示される', async () => {
    // Arrange
    server.use(http.get('*/api/todos', () => HttpResponse.json([])))

    // Act
    renderRoute({ initialEntries: ['/todos'] })

    // Assert
    await waitFor(() => {
      expect(screen.getByText('TODOがありません')).toBeInTheDocument()
    })
  })

  it('入力が空のとき追加ボタンは無効', async () => {
    // Act
    renderRoute({ initialEntries: ['/todos'] })
    await waitFor(() =>
      expect(
        screen.getByPlaceholderText('新しいTODOを入力...'),
      ).toBeInTheDocument(),
    )

    // Assert
    expect(screen.getByRole('button', { name: '追加' })).toBeDisabled()
  })

  it('TODO を追加できる', async () => {
    // Arrange — POST 後の GET で新しいアイテムを含むレスポンスを返す
    const newTodo: Todo = {
      id: 100,
      userId: 99,
      text: '新しいタスク',
      done: false,
      createdAt: '2026-01-03T00:00:00Z',
      updatedAt: '2026-01-03T00:00:00Z',
    }
    let posted = false
    server.use(
      http.get('*/api/todos', () =>
        HttpResponse.json(posted ? [...mockTodos, newTodo] : mockTodos),
      ),
      http.post('*/api/todos', async () => {
        posted = true
        return HttpResponse.json(newTodo, { status: 201 })
      }),
    )
    const user = userEvent.setup()
    renderRoute({ initialEntries: ['/todos'] })
    await waitFor(() =>
      expect(screen.getByText('牛乳を買う')).toBeInTheDocument(),
    )

    // Act
    await user.type(
      screen.getByPlaceholderText('新しいTODOを入力...'),
      '新しいタスク',
    )
    await user.click(screen.getByRole('button', { name: '追加' }))

    // Assert
    await waitFor(() => {
      expect(screen.getByText('新しいタスク')).toBeInTheDocument()
    })
    expect(screen.getByPlaceholderText('新しいTODOを入力...')).toHaveValue('')
  })

  it('チェックボックスで完了状態を切り替えられる', async () => {
    // Arrange — PUT 後の GET で done=true を返す
    const toggled: Todo = { ...mockTodos[0], done: true }
    let toggled_ = false
    server.use(
      http.get('*/api/todos', () =>
        HttpResponse.json(toggled_ ? [toggled, mockTodos[1]] : mockTodos),
      ),
      http.put('*/api/todos/:id', async () => {
        toggled_ = true
        return HttpResponse.json(toggled)
      }),
    )
    const user = userEvent.setup()
    renderRoute({ initialEntries: ['/todos'] })
    await waitFor(() =>
      expect(screen.getByText('牛乳を買う')).toBeInTheDocument(),
    )

    // Act — 未完了の「牛乳を買う」（id 降順なので checkboxes[1]）を完了にする
    const checkboxes = screen.getAllByRole('checkbox')
    await user.click(checkboxes[1])

    // Assert
    await waitFor(() => {
      expect(screen.getAllByRole('checkbox')[1]).toBeChecked()
    })
  })

  it('削除ボタンで TODO を削除できる', async () => {
    // Arrange — DELETE 後の GET から該当アイテムを除く
    let deleted = false
    server.use(
      http.get('*/api/todos', () =>
        HttpResponse.json(deleted ? [mockTodos[1]] : mockTodos),
      ),
      http.delete('*/api/todos/:id', () => {
        deleted = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const user = userEvent.setup()
    renderRoute({ initialEntries: ['/todos'] })
    await waitFor(() =>
      expect(screen.getByText('牛乳を買う')).toBeInTheDocument(),
    )

    // Act — 「牛乳を買う」の削除ボタンをクリック (id 降順なので [1])
    const deleteButtons = screen.getAllByRole('button', { name: '削除' })
    await user.click(deleteButtons[1])

    // Assert
    await waitFor(() => {
      expect(screen.queryByText('牛乳を買う')).not.toBeInTheDocument()
    })
    expect(screen.getByText('メールを返信する')).toBeInTheDocument()
  })

  it('API エラー時はエラー表示ではなく空リストを表示する', async () => {
    // Arrange — GET を失敗させる
    server.use(
      http.get('*/api/todos', () =>
        HttpResponse.json({ status: 500 }, { status: 500 }),
      ),
    )

    // Act
    renderRoute({ initialEntries: ['/todos'] })

    // Assert — API エラーでもクラッシュせず、IDB が空なので「TODOがありません」を表示
    // （fetchAndSyncTodos は fetch 失敗時に catch して getTodos() にフォールバックする）
    await waitFor(
      () => {
        expect(screen.queryByText('読み込み中...')).not.toBeInTheDocument()
      },
      { timeout: 3000 },
    )
    expect(
      screen.queryByText('TODOの取得に失敗しました'),
    ).not.toBeInTheDocument()
  })

  it('未同期キューが残っているだけでは同期失敗アラートを表示しない', async () => {
    // Arrange
    const { enqueueSyncOp, resetDB, upsertTodo } =
      await import('@/lib/todo-store')
    await resetDB()
    await upsertTodo({
      localId: 'local-pending',
      serverId: null,
      userId: 0,
      text: '未同期のTODO',
      done: false,
      updatedAt: '2026-04-27T00:00:00.000Z',
      syncStatus: 'pending',
    })
    await enqueueSyncOp({
      type: 'create',
      localId: 'local-pending',
      payload: { text: '未同期のTODO', done: false },
    })

    // Act
    renderRoute({ initialEntries: ['/todos'] })

    // Assert
    await waitFor(() => {
      expect(screen.getByText('未同期のTODO')).toBeInTheDocument()
    })
    expect(
      screen.queryByText('同期できない変更があります。'),
    ).not.toBeInTheDocument()
  })

  it('同期失敗後に未同期キューが残っていると同期失敗アラートを表示する', async () => {
    // Arrange
    const { enqueueSyncOp, resetDB, syncFailureQueryKey, upsertTodo } =
      await import('@/lib/todo-store')
    await resetDB()
    await upsertTodo({
      localId: 'local-failed',
      serverId: null,
      userId: 0,
      text: '同期失敗したTODO',
      done: false,
      updatedAt: '2026-04-27T00:00:00.000Z',
      syncStatus: 'pending',
    })
    await enqueueSyncOp({
      type: 'create',
      localId: 'local-failed',
      payload: { text: '同期失敗したTODO', done: false },
    })

    // Act
    const { queryClient } = renderRoute({ initialEntries: ['/todos'] })
    queryClient.setQueryData(syncFailureQueryKey, true)

    // Assert
    await waitFor(() => {
      expect(
        screen.getByText('同期できない変更があります。'),
      ).toBeInTheDocument()
    })
    expect(screen.getByRole('button', { name: '再試行' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '破棄' })).toBeInTheDocument()
  })
})
