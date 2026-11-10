'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Badge,
  Button,
  Card as UICard,
  EmptyState,
  ErrorBox,
  Field,
  Modal,
  PageHeader,
  SkeletonCard,
  createCard,
  formatCents,
  formatDateTime,
  getCustomerAccounts,
  inputClass,
  listCards,
  maskUuid,
  parseApiError,
  updateCardStatus,
  useAuth,
  type Card,
  type CardStatus,
} from '@novo/shared';
import { issueCardSchema, type IssueCardFormValues } from './schema';

export default function CardsPage() {
  const { customerId, accountId: currentAccountId } = useAuth();
  const queryClient = useQueryClient();

  const [isIssueModalOpen, setIsIssueModalOpen] = useState(false);
  const [cardToToggle, setCardToToggle] = useState<Card | null>(null);

  const cardsQuery = useQuery({
    queryKey: ['cards'],
    queryFn: listCards,
  });

  const accountsQuery = useQuery({
    queryKey: ['customer-accounts', customerId],
    queryFn: () => getCustomerAccounts(customerId!),
    enabled: !!customerId && isIssueModalOpen,
  });

  const {
    register,
    handleSubmit,
    reset: resetForm,
    formState: { errors, isSubmitting },
  } = useForm<IssueCardFormValues>({
    resolver: zodResolver(issueCardSchema),
    defaultValues: {
      accountId: currentAccountId || '',
    },
  });

  const issueMutation = useMutation({
    mutationFn: (values: IssueCardFormValues) => createCard({ accountId: values.accountId }),
    onSuccess: async () => {
      setIsIssueModalOpen(false);
      resetForm();
      await queryClient.invalidateQueries({ queryKey: ['cards'] });
    },
  });

  const statusMutation = useMutation({
    mutationFn: ({ cardId, nextStatus }: { cardId: string; nextStatus: CardStatus }) =>
      updateCardStatus(cardId, { status: nextStatus }),
    onSuccess: async () => {
      setCardToToggle(null);
      await queryClient.invalidateQueries({ queryKey: ['cards'] });
    },
  });

  const getStatusBadge = (status: CardStatus) => {
    switch (status) {
      case 'ACTIVE':
        return <Badge tone="green">ACTIVE</Badge>;
      case 'FROZEN':
        return <Badge tone="yellow">FROZEN</Badge>;
      case 'CANCELLED':
        return <Badge tone="red">CANCELLED</Badge>;
      default:
        return <Badge tone="gray">{status}</Badge>;
    }
  };

  const onSubmitIssue = (values: IssueCardFormValues) => {
    issueMutation.mutate(values);
  };

  return (
    <>
      <PageHeader
        title="Debit Cards"
        actions={
          <Button onClick={() => setIsIssueModalOpen(true)}>Issue New Card</Button>
        }
      />

      {cardsQuery.isLoading ? (
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          <SkeletonCard rows={3} />
          <SkeletonCard rows={3} />
        </div>
      ) : null}

      {cardsQuery.isError ? (
        <ErrorBox>{parseApiError(cardsQuery.error).message}</ErrorBox>
      ) : null}

      {cardsQuery.data ? (
        cardsQuery.data.length === 0 ? (
          <EmptyState
            title="No cards issued"
            description="You don't have any debit cards yet. Issue a new virtual debit card for your account."
            action={<Button onClick={() => setIsIssueModalOpen(true)}>Issue your first card</Button>}
          />
        ) : (
          <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
            {cardsQuery.data.map((card) => {
              const isFrozen = card.status === 'FROZEN';
              const isActive = card.status === 'ACTIVE';

              return (
                <div
                  key={card.cardId}
                  className={`relative flex flex-col justify-between overflow-hidden rounded-xl border p-6 shadow-sm transition ${
                    isFrozen
                      ? 'border-amber-200 bg-amber-50/40'
                      : card.status === 'CANCELLED'
                      ? 'border-slate-200 bg-slate-50 opacity-60'
                      : 'border-slate-800 bg-gradient-to-br from-slate-900 via-slate-800 to-slate-950 text-white'
                  }`}
                >
                  <div>
                    <div className="flex items-center justify-between">
                      <span className={`text-xs font-semibold tracking-wider uppercase ${isActive ? 'text-slate-300' : 'text-slate-600'}`}>
                        Novo Virtual Debit
                      </span>
                      {getStatusBadge(card.status)}
                    </div>

                    <div className="my-6">
                      <p className={`font-mono text-xl tracking-widest ${isActive ? 'text-white' : 'text-slate-800'}`}>
                        {card.maskedNumber}
                      </p>
                    </div>

                    <div className="flex items-center justify-between text-xs">
                      <div>
                        <span className={`block uppercase ${isActive ? 'text-slate-400' : 'text-slate-500'}`}>Expires</span>
                        <span className={`font-mono font-medium ${isActive ? 'text-slate-200' : 'text-slate-800'}`}>
                          {String(card.expiryMonth).padStart(2, '0')}/{String(card.expiryYear).slice(-2)}
                        </span>
                      </div>
                      <div>
                        <span className={`block uppercase ${isActive ? 'text-slate-400' : 'text-slate-500'}`}>Daily Limit</span>
                        <span className={`font-medium ${isActive ? 'text-slate-200' : 'text-slate-800'}`}>
                          {formatCents(card.dailyLimitCents)}
                        </span>
                      </div>
                    </div>
                  </div>

                  <div className={`mt-6 pt-4 border-t flex items-center justify-between gap-2 ${isActive ? 'border-slate-700' : 'border-slate-200'}`}>
                    <span className={`text-xs font-mono ${isActive ? 'text-slate-400' : 'text-slate-500'}`}>
                      Acc: {maskUuid(card.accountId)}
                    </span>
                    {card.status !== 'CANCELLED' ? (
                      <Button
                        variant={isFrozen ? 'primary' : 'secondary'}
                        className="text-xs px-3 py-1.5"
                        onClick={() => setCardToToggle(card)}
                      >
                        {isFrozen ? 'Unfreeze' : 'Freeze'}
                      </Button>
                    ) : null}
                  </div>
                </div>
              );
            })}
          </div>
        )
      ) : null}

      {/* Issue New Card Modal */}
      <Modal
        isOpen={isIssueModalOpen}
        onClose={() => {
          setIsIssueModalOpen(false);
          resetForm();
        }}
        title="Issue New Debit Card"
        description="Select the source checking or savings account for this virtual card."
        footer={
          <>
            <Button
              variant="secondary"
              type="button"
              onClick={() => {
                setIsIssueModalOpen(false);
                resetForm();
              }}
              disabled={issueMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              form="issue-card-form"
              disabled={isSubmitting || issueMutation.isPending}
            >
              {isSubmitting || issueMutation.isPending ? 'Issuing...' : 'Issue Card'}
            </Button>
          </>
        }
      >
        <form id="issue-card-form" onSubmit={handleSubmit(onSubmitIssue)} className="space-y-4" noValidate>
          {issueMutation.isError ? (
            <ErrorBox>{parseApiError(issueMutation.error).message}</ErrorBox>
          ) : null}

          <Field label="Link to Account" error={errors.accountId?.message}>
            {accountsQuery.isLoading ? (
              <p className="text-sm text-slate-500">Loading accounts...</p>
            ) : (
              <select {...register('accountId')} className={inputClass}>
                <option value="">Select an account...</option>
                {accountsQuery.data?.map((acc) => (
                  <option key={acc.id} value={acc.id}>
                    {acc.accountType.toUpperCase()} — {acc.accountNumber} ({formatCents(acc.balanceCents)})
                  </option>
                ))}
              </select>
            )}
          </Field>
          <p className="text-xs text-slate-500">
            Virtual cards are issued instantly with a daily spending limit of $5,000.00.
          </p>
        </form>
      </Modal>

      {/* Freeze / Unfreeze Confirmation Modal */}
      <Modal
        isOpen={!!cardToToggle}
        onClose={() => setCardToToggle(null)}
        title={cardToToggle?.status === 'ACTIVE' ? 'Freeze Debit Card' : 'Unfreeze Debit Card'}
        description={
          cardToToggle?.status === 'ACTIVE'
            ? 'Freezing will immediately decline all new authorizations until you unfreeze it.'
            : 'Unfreezing will re-enable authorizations on this debit card.'
        }
        footer={
          <>
            <Button
              variant="secondary"
              onClick={() => setCardToToggle(null)}
              disabled={statusMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              variant={cardToToggle?.status === 'ACTIVE' ? 'danger' : 'primary'}
              onClick={() => {
                if (cardToToggle) {
                  const nextStatus = cardToToggle.status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE';
                  statusMutation.mutate({ cardId: cardToToggle.cardId, nextStatus });
                }
              }}
              disabled={statusMutation.isPending}
            >
              {statusMutation.isPending
                ? 'Updating...'
                : cardToToggle?.status === 'ACTIVE'
                ? 'Freeze Card'
                : 'Unfreeze Card'}
            </Button>
          </>
        }
      >
        {cardToToggle ? (
          <div className="space-y-2 text-sm text-slate-700">
            {statusMutation.isError ? (
              <ErrorBox>{parseApiError(statusMutation.error).message}</ErrorBox>
            ) : null}
            <div className="flex justify-between border-b pb-2">
              <span className="text-slate-500">Card Number:</span>
              <span className="font-mono font-semibold text-slate-900">{cardToToggle.maskedNumber}</span>
            </div>
            <div className="flex justify-between pt-1">
              <span className="text-slate-500">Current Status:</span>
              <span>{getStatusBadge(cardToToggle.status)}</span>
            </div>
          </div>
        ) : null}
      </Modal>
    </>
  );
}
