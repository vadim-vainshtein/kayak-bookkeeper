package kz.kayak.bookkeeper.domain;

import jakarta.persistence.*;

@Entity @Table(name = "subscription_members", uniqueConstraints = @UniqueConstraint(columnNames = {"subscription_id", "person_id"}))
public class SubscriptionMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private Subscription subscription;
    @ManyToOne(optional = false) private Person person;
    @Column(nullable = false) private boolean usesSkirt;
    @Column(nullable = false) private boolean usesPaddle;
    @Column(nullable = false) private boolean usesKayak;
    private Long poolShare;
    private Long skirtFee;
    private Long paddleFee;
    private Long kayakFee;
    protected SubscriptionMember() { }
    public SubscriptionMember(Subscription subscription, Person person, boolean usesSkirt, boolean usesPaddle, boolean usesKayak) { this.subscription=subscription; this.person=person; this.usesSkirt=usesSkirt; this.usesPaddle=usesPaddle; this.usesKayak=usesKayak; }
    public Long getId() { return id; } public Subscription getSubscription() { return subscription; } public Person getPerson() { return person; } public boolean isUsesSkirt() { return usesSkirt; } public boolean isUsesPaddle() { return usesPaddle; } public boolean isUsesKayak() { return usesKayak; } public Long getPoolShare() { return poolShare; } public Long getSkirtFee() { return skirtFee; } public Long getPaddleFee() { return paddleFee; } public Long getKayakFee() { return kayakFee; }
    public void snapshotFees(long poolShare, long skirtFee, long paddleFee, long kayakFee) { this.poolShare=poolShare; this.skirtFee=skirtFee; this.paddleFee=paddleFee; this.kayakFee=kayakFee; }
    public void editAutomaticFee(ChargeType type, long amount) {
        switch (type) {
            case POOL_SHARE -> poolShare = amount;
            case SKIRT_RENT -> skirtFee = amount;
            case PADDLE_RENT -> paddleFee = amount;
            case KAYAK_RENT -> kayakFee = amount;
            default -> throw new IllegalArgumentException("Charge type does not belong to a subscription member fee");
        }
    }
}
